package com.dippie.service.catalog;

import com.dippie.common.rating.RatingLookup;
import com.dippie.common.rating.RatingSummary;
import com.dippie.common.text.TextFolding;
import com.dippie.dto.catalog.CatalogSearchHit;
import com.dippie.service.catalog.CatalogReadModel.SearchRank;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

// service tìm kiếm nhanh cho ô search danh mục, chạy thẳng trên SearchIndex trong cache
@Service
public class CatalogSearchService {

    private final CatalogReadModelCache cache;
    private final RatingLookup ratings;

    public CatalogSearchService(CatalogReadModelCache cache, RatingLookup ratings) {
        this.cache = cache;
        this.ratings = ratings;
    }

    @Transactional(readOnly = true)
    public List<CatalogSearchHit> search(String query, boolean authenticated, boolean admin) {
        return search(query, authenticated, admin, null);
    }

    // tìm theo quyền người xem, chặn tối đa 500 kết quả rồi kéo gộp rating cho mấy bộ với tập phim
    @Transactional(readOnly = true)
    public List<CatalogSearchHit> search(String query, boolean authenticated, boolean admin, Integer limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int effectiveLimit = (limit == null || limit <= 0)
                ? 200
                : Math.min(limit, 500);
        var hits = cache.current().searchIndex().ranked(query, authenticated, admin)
                .limit(effectiveLimit)
                .map(RankedRow::hit).toList();
        var summaries = ratings.byId(hits.stream().filter(CatalogSearchHit::titleLevel).map(CatalogSearchHit::id).toList());
        return hits.stream().map(hit -> hit.titleLevel()
                ? hit.withRatings(summaries.getOrDefault(hit.id(), RatingSummary.EMPTY)) : hit).toList();
    }

    public static String firstText(String text, String fallback) {
        return text == null || text.isEmpty() ? fallback : text;
    }

    private static String folded(String text) {
        return TextFolding.foldNormalized(text);
    }

    // chỉ mục trong RAM giữ sẵn mấy dòng tìm kiếm, tập từ chuẩn với bảng posting chia theo độ dài để lọc ứng viên khớp mờ cho nhanh
    public record SearchIndex(List<SearchRow> rows, Set<String> exactWords, Map<String, BitSet> topicPostings,
            Map<Integer, Map<String, BitSet>> topicPostingsByLength) {

        // lập sẵn tập từ đúng chính tả với bảng posting theo độ dài cho mấy cụm chủ đề của bộ với tập
        public static SearchIndex from(List<SearchRow> rows) {
            List<SearchRow> copy = List.copyOf(rows);
            Set<String> exactWords = new HashSet<>();
            Map<String, BitSet> topicPostings = new HashMap<>();
            Map<Integer, Map<String, BitSet>> byLength = new HashMap<>();
            for (int index = 0; index < copy.size(); index++) {
                SearchRow row = copy.get(index);
                for (String name : row.names()) {
                    exactWords.addAll(NameSimilarity.words(name));
                }
                for (String field : row.fields()) {
                    exactWords.addAll(NameSimilarity.words(field));
                }
                if (row.hit().titleLevel()) {
                    for (List<String> phrase : row.topicPhrases()) {
                        for (String word : phrase) {
                            int wordLength = word.codePointCount(0, word.length());
                            if (wordLength >= 3) {
                                BitSet bits = topicPostings.computeIfAbsent(word, ignored -> new BitSet(copy.size()));
                                bits.set(index);
                                byLength.computeIfAbsent(wordLength, ignored -> new HashMap<>()).put(word, bits);
                            }
                        }
                    }
                }
            }
            return new SearchIndex(copy, Set.copyOf(exactWords), Map.copyOf(topicPostings), Map.copyOf(byLength));
        }

        // chấm hạng mấy hàng được phép xem rồi xếp theo ưu tiên của SearchRank
        public Stream<RankedRow> ranked(String query, boolean authenticated, boolean admin) {
            String term = folded(query);
            if (term.isEmpty()) {
                return Stream.empty();
            }
            var words = NameSimilarity.words(term);
            Predicate<String> allowShortFuzzy = word -> !exactWords.contains(word);

            // chấm hạng hết các bộ phim trước để lượt quét sau mấy tập con kế thừa luôn hạng từ bộ cha
            Map<String, SearchRank> seriesRanks = new HashMap<>();
            for (SearchRow row : rows) {
                if (row.visibleTo(authenticated, admin) && row.hit().type().equals("series")) {
                    var rank = row.seriesRank(term, words, allowShortFuzzy);
                    if (rank != null) {
                        seriesRanks.put(row.hit().id(), rank);
                    }
                }
            }

            // thu hẹp ứng viên bằng giao BitSet trước rồi mới chạy so khớp mờ theo cụm chủ đề
            BitSet topicCandidates = topicCandidates(words, allowShortFuzzy);
            Stream.Builder<RankedRow> matches = Stream.builder();
            for (int index = 0; index < rows.size(); index++) {
                SearchRow row = rows.get(index);
                if (!row.visibleTo(authenticated, admin)) {
                    continue;
                }
                SearchRank rank = row.rank(term, words, seriesRanks, allowShortFuzzy);
                if (rank == null && topicCandidates.get(index) && row.topicFuzzyMatches(words, allowShortFuzzy)) {
                    rank = SearchRank.FUZZY;
                }
                if (rank != null) {
                    matches.add(new RankedRow(row.hit(), rank));
                }
            }
            return matches.build().sorted(Comparator.comparing(RankedRow::rank));
        }

        // giao mấy BitSet ứng viên của từng từ trong nhóm độ dài sát nhau để chỉ phải khớp cụm trên một góc nhỏ danh mục
        private BitSet topicCandidates(List<String> words, Predicate<String> allowShortFuzzy) {
            if (words.isEmpty() || words.stream().noneMatch(word -> word.codePointCount(0, word.length()) >= 4)) {
                return new BitSet();
            }
            boolean allowShortDeletion = words.size() >= 2;
            BitSet intersection = null;
            for (String word : words) {
                BitSet matched = new BitSet(rows.size());
                BitSet exact = topicPostings.get(word);
                if (exact != null) {
                    matched.or(exact);
                }
                int wordLength = word.codePointCount(0, word.length());
                if (exact == null || wordLength >= 4) {
                    int minLength = wordLength == 3 ? 3 : Math.max(4, wordLength - (wordLength <= 7 ? 1 : 2));
                    int maxLength = wordLength == 3 ? (allowShortDeletion ? 4 : 3) : wordLength + (wordLength <= 7 ? 1 : 2);
                    for (int len = minLength; len <= maxLength; len++) {
                        Map<String, BitSet> bucket = topicPostingsByLength.get(len);
                        if (bucket == null) {
                            continue;
                        }
                        bucket.forEach((candidate, postings) -> {
                            if (NameSimilarity.topicWordDistance(word, candidate, allowShortDeletion, allowShortFuzzy) > 0) {
                                matched.or(postings);
                            }
                        });
                    }
                }
                if (matched.isEmpty()) {
                    return new BitSet();
                }
                if (intersection == null) {
                    intersection = matched;
                } else {
                    intersection.and(matched);
                    if (intersection.isEmpty()) {
                        return intersection;
                    }
                }
            }
            return intersection == null ? new BitSet() : intersection;
        }
    }

    // kết quả tìm kiếm kẹp hạng khớp để dễ sort
    public record RankedRow(CatalogSearchHit hit, SearchRank rank) {
    }

    // một dòng dữ liệu đã bỏ dấu, hạ chữ thường sẵn tên, mô tả, quyền xem với cụm chủ đề cho tìm kiếm trong RAM
    public record SearchRow(CatalogSearchHit hit, boolean publicVisible, boolean memberVisible, List<String> names, List<String> fields,
            List<List<String>> seriesNames, List<List<String>> fuzzyNames, List<List<String>> topicPhrases) {

        public static SearchRow of(CatalogSearchHit hit, boolean publicVisible, boolean memberVisible, String... fields) {
            return named(hit, publicVisible, memberVisible, List.of(), List.of(), List.of(), fields);
        }

        // bỏ dấu sẵn tên với mô tả, tiện tách luôn danh sách từ cho bước khớp mờ
        public static SearchRow named(CatalogSearchHit hit, boolean publicVisible, boolean memberVisible, List<String> names,
                List<String> characters, List<String> fuzzyAliases, String... fields) {
            var foldedNames = names.stream().map(CatalogSearchService::folded).distinct().toList();
            var foldedFields = Stream.concat(characters.stream(), Arrays.stream(fields).filter(Objects::nonNull))
                    .map(CatalogSearchService::folded).distinct().toList();
            var seriesNames = hit.type().equals("series") ? names.stream().map(NameSimilarity::words).distinct().toList()
                    : List.<List<String>>of();
            var fuzzyNames = Stream.concat(characters.stream(), fuzzyAliases.stream())
                    .map(NameSimilarity::words).distinct().toList();
            var topicPhrases = hit.titleLevel()
                    ? Stream.concat(names.stream(), Arrays.stream(fields).filter(Objects::nonNull))
                            .map(NameSimilarity::words).filter(phrase -> !phrase.isEmpty()).distinct().toList()
                    : List.<List<String>>of();
            return new SearchRow(hit, publicVisible, memberVisible, foldedNames, foldedFields, seriesNames, fuzzyNames,
                    topicPhrases);
        }

        public boolean visibleTo(boolean authenticated, boolean admin) {
            return admin || (authenticated ? memberVisible : publicVisible);
        }

        // xếp hạng riêng cho bộ phim, khớp đúng tên trước, chứa chuỗi con sau rồi mới tới khớp mờ
        public SearchRank seriesRank(String term, List<String> words, Predicate<String> allowShortFuzzy) {
            if (names.contains(term)) {
                return SearchRank.SERIES_EXACT;
            }
            if (names.stream().anyMatch(name -> name.contains(term))) {
                return SearchRank.SERIES_PARTIAL;
            }
            return fuzzyMatches(seriesNames, words, allowShortFuzzy) ? SearchRank.SERIES_FUZZY : null;
        }

        // tính hạng cho một dòng, ưu tiên khớp chuẩn của chính nó hay bộ phim cha trước khi rơi xuống khớp mờ
        public SearchRank rank(String term, List<String> words, Map<String, SearchRank> seriesRanks,
                Predicate<String> allowShortFuzzy) {
            var seriesRank = seriesRanks.get(hit.id());
            if (seriesRank != null && !seriesRank.fuzzy()) {
                return seriesRank;
            }
            var parent = hit.type().equals("episode") ? seriesRanks.get(hit.seriesId()) : null;
            if ((parent != null && !parent.fuzzy())
                    || Stream.concat(names.stream(), fields.stream()).anyMatch(field -> field.contains(term))) {
                return SearchRank.EXACT;
            }
            if (seriesRank != null) {
                return seriesRank;
            }
            return parent != null || fuzzyMatches(fuzzyNames, words, allowShortFuzzy) ? SearchRank.FUZZY : null;
        }

        public boolean topicFuzzyMatches(List<String> words, Predicate<String> allowShortFuzzy) {
            return topicPhrases.stream()
                    .anyMatch(phrase -> NameSimilarity.topicPhraseDistance(words, phrase, allowShortFuzzy) > 0);
        }

        private static boolean fuzzyMatches(List<List<String>> names, List<String> words,
                Predicate<String> allowShortFuzzy) {
            return !words.isEmpty() && names.stream().filter(name -> name.size() >= words.size())
                    .anyMatch(name -> NameSimilarity.phraseDistance(words, name, allowShortFuzzy) > 0);
        }
    }

}
