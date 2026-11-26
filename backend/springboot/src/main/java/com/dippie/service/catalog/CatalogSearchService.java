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

// Dịch vụ tìm kiếm nhanh trên ô tìm kiếm danh mục, chạy thẳng trên SearchIndex trong bộ nhớ đệm.
@Service
public class CatalogSearchService {

    private static final int DEFAULT_SEARCH_LIMIT = 200;
    private static final int MAX_SEARCH_LIMIT = 500;

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

    // Chạy tìm kiếm theo vai trò người xem, chặn giới hạn tối đa 500 kết quả rồi nạp gộp điểm đánh giá cho các bộ/tập phim.
    @Transactional(readOnly = true)
    public List<CatalogSearchHit> search(String query, boolean authenticated, boolean admin, Integer limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int effectiveLimit = (limit == null || limit <= 0)
                ? DEFAULT_SEARCH_LIMIT
                : Math.min(limit, MAX_SEARCH_LIMIT);
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

    // Chỉ mục trong bộ nhớ giữ sẵn các dòng tìm kiếm, tập từ chính xác và bảng posting gom theo độ dài để lọc ứng viên khớp mờ nhanh.
    public record SearchIndex(List<SearchRow> rows, Set<String> exactWords, Map<String, BitSet> topicPostings,
            Map<Integer, Map<String, BitSet>> topicPostingsByLength) {

        // Lập trước tập từ đúng chính tả và bảng posting theo độ dài cho các cụm từ chủ đề của bộ/tập phim.
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

        // Chấm hạng các hàng được phép xem rồi sắp theo độ ưu tiên của SearchRank.
        public Stream<RankedRow> ranked(String query, boolean authenticated, boolean admin) {
            String term = folded(query);
            if (term.isEmpty()) {
                return Stream.empty();
            }
            var words = NameSimilarity.words(term);
            Predicate<String> allowShortFuzzy = word -> !exactWords.contains(word);

            // Chấm hạng tất cả bộ phim trước để các tập phim con kế thừa hạng từ bộ phim cha ở lượt quét sau.
            Map<String, SearchRank> seriesRanks = new HashMap<>();
            for (SearchRow row : rows) {
                if (row.visibleTo(authenticated, admin) && row.hit().type().equals("series")) {
                    var rank = row.seriesRank(term, words, allowShortFuzzy);
                    if (rank != null) {
                        seriesRanks.put(row.hit().id(), rank);
                    }
                }
            }

            // Thu hẹp ứng viên bằng giao BitSet trước khi chạy so khớp mờ theo cụm chủ đề.
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

        // Giao các tập bit ứng viên của từng từ khóa trong nhóm độ dài lân cận để chỉ phải chạy so khớp cụm trên một phần nhỏ danh mục.
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

    // Cặp kết quả tìm kiếm kèm hạng khớp dùng để sắp xếp.
    public record RankedRow(CatalogSearchHit hit, SearchRank rank) {
    }

    // Một dòng dữ liệu đã bỏ dấu và chuyển chữ thường sẵn tên, trường mô tả, quyền xem và cụm từ chủ đề cho tìm kiếm trong bộ nhớ.
    public record SearchRow(CatalogSearchHit hit, boolean publicVisible, boolean memberVisible, List<String> names, List<String> fields,
            List<List<String>> seriesNames, List<List<String>> fuzzyNames, List<List<String>> topicPhrases) {

        public static SearchRow of(CatalogSearchHit hit, boolean publicVisible, boolean memberVisible, String... fields) {
            return named(hit, publicVisible, memberVisible, List.of(), List.of(), List.of(), fields);
        }

        // Tiền xử lý chữ bỏ dấu cho tên, trường mô tả và tách sẵn danh sách từ cho bước khớp mờ.
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

        // Xếp hạng riêng cho bộ phim theo thứ tự khớp đúng tên, chứa chuỗi con rồi mới đến khớp mờ.
        public SearchRank seriesRank(String term, List<String> words, Predicate<String> allowShortFuzzy) {
            if (names.contains(term)) {
                return SearchRank.SERIES_EXACT;
            }
            if (names.stream().anyMatch(name -> name.contains(term))) {
                return SearchRank.SERIES_PARTIAL;
            }
            return fuzzyMatches(seriesNames, words, allowShortFuzzy) ? SearchRank.SERIES_FUZZY : null;
        }

        // Tính hạng cho một dòng: ưu tiên hạng khớp chính xác của bản thân hoặc bộ phim cha trước khi rơi xuống khớp mờ.
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
