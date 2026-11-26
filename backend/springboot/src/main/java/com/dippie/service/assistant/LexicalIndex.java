package com.dippie.service.assistant;

import com.dippie.common.collection.DiverseSelection;
import com.dippie.common.rating.RatingSummary;
import com.dippie.common.text.UnicodeText;
import com.dippie.model.assistant.SearchConstraints;
import com.dippie.model.assistant.SearchDocument;
import com.dippie.model.assistant.SearchEvidence;
import com.dippie.model.assistant.SearchHit;
import com.dippie.model.catalog.EpisodeMood;
import com.dippie.model.catalog.EpisodeRuntime;
import com.dippie.service.catalog.NameSimilarity;
import com.dippie.service.catalog.SeriesSearchAliases;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Chỉ mục tìm kiếm toàn văn trong bộ nhớ chạy BM25 kèm sửa lỗi chính tả 1 đến 2 ký tự cho tên phim, nhân vật và chủ đề.
// Khi truy vấn tới, lớp này tách riêng vế phủ định ("không phải", "bỏ qua", "trừ"), chấm điểm BM25 kết hợp RRF và xoay vòng đa dạng theo bộ phim ở chế độ khám phá.
public final class LexicalIndex {

    private static final int CANDIDATE_LIMIT = 30;
    private static final int MAX_EVIDENCE_LENGTH = 1200;
    private static final int RRF_K = 61;
    private static final double DEFAULT_RATING = 8.0;
    private static final String TYPE_SERIES = "series";
    private static final String TYPE_EPISODE = "episode";
    private static final String TYPE_SEGMENT = "segment";
    private static final String TIMELINE_FILE = "file";
    private static final String TIMELINE_EPISODE = "episode";
    private static final String WORD = "[\\p{L}\\p{N}_]";
    private static final String SPACE = UnicodeText.SPACE;
    private static final String NUMBER_PREFIX = SPACE + "*(?:#" + SPACE + "*)?";
    private static final String NUMBER = "(\\p{Nd}+)";
    private static final Pattern TOKEN = Pattern.compile(WORD + "+");
    private static final Pattern MARK = Pattern.compile("\\p{Mn}");
    private static final Pattern NEGATION = bounded("(?:(?:(?:chứ|chu)" + SPACE + "+)?(?:không|khong)"
            + SPACE + "+(?:phải|phai|xem)|(?:bỏ|bo)" + SPACE + "+qua|trừ|tru(?=" + SPACE
            + "+(?:s\\p{Nd}+e\\p{Nd}+(?!" + WORD + ")|(?:mùa|mua|season|phần|phan|tập|tap|ep|episode)"
            + NUMBER_PREFIX + "\\p{Nd})))");
    private static final Pattern TIMECODE = bounded("(?:(?:phut|giay)" + SPACE
            + "+(?:nao|may|thu may|bao nhieu)|moc thoi gian|timecode|timestamp)");
    private static final Pattern COMBINED = bounded("s" + NUMBER + "e" + NUMBER);
    private static final Pattern SEASON = numbered("mua|season|phan");
    private static final Pattern EPISODE = numbered("tap|ep|episode");
    private static final Comparator<SearchDocument> SEGMENT_ORDER = Comparator
            .comparing(SearchDocument::segmentPosition, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(item -> item.timecode().episodeStart(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SearchDocument::id);
    private static final Map<String, Object> DIAGNOSTICS = Map.of("retrieval", "bm25", "denseReady", false,
            "missingEmbeddings", 0, "calibrated", false);

    private final List<SearchDocument> documents;
    private final Map<String, SearchDocument> documentsById;
    private final Map<String, Term> terms;
    private final float[] lengthPenalties;
    private final int[] baseOrdinals;
    private final int[] calmScores;
    private final BitSet compilationDocuments;
    private final BitSet intenseDocuments;
    private final List<Set<String>> genres;
    private final Map<String, List<List<String>>> seriesNames;
    private final Map<Integer, Map<String, BitSet>> namePostingsByLength;
    private final Map<Integer, Map<String, BitSet>> topicPostingsByLength;
    private final Map<Integer, List<List<String>>> topicPhrases;
    private final Map<String, Integer> seriesEpisodeCounts;
    private final Map<String, Integer> seriesSeasonCounts;
    private final List<SearchDocument> seriesDocuments;
    private final List<String> cachedSeriesTitles;
    private final Map<String, SeriesTags> seriesTags;
    private final Map<String, List<SearchDocument>> segmentsByEpisode;

    // Dựng sẵn toàn bộ chỉ mục ngược BM25, bảng tên/chủ đề gom theo độ dài từ và thống kê mùa/tập để lúc tìm không phải quét lại.
    public LexicalIndex(List<SearchDocument> documents) {
        // Gom bộ phim, sắp phân đoạn theo thứ tự trong từng tập và đếm sẵn số tập, số mùa mỗi bộ.
        this.documents = List.copyOf(documents);
        seriesDocuments = this.documents.stream().filter(item -> item.itemType().equals(TYPE_SERIES)).toList();
        cachedSeriesTitles = seriesDocuments.stream().map(item -> item.text().title())
                .filter(title -> title != null && !title.isBlank()).distinct().sorted().toList();
        seriesTags = new HashMap<>();
        seriesDocuments.forEach(item -> seriesTags.put(item.id(), new SeriesTags(
                normalized(item.placement().genreLabels()),
                normalized(item.text().themes()))));
        segmentsByEpisode = this.documents.stream()
                .filter(item -> item.itemType().equals(TYPE_SEGMENT) && episodeId(item) != null)
                .sorted(SEGMENT_ORDER).collect(Collectors.groupingBy(LexicalIndex::episodeId));
        seriesEpisodeCounts = new HashMap<>();
        Map<String, Set<Integer>> seriesSeasons = new HashMap<>();
        for (SearchDocument item : this.documents) {
            if (item.itemType().equals(TYPE_EPISODE)) {
                String seriesId = item.placement().seriesId();
                seriesEpisodeCounts.merge(seriesId, 1, Integer::sum);
                Integer season = item.placement().seasonNumber();
                if (season != null) {
                    seriesSeasons.computeIfAbsent(seriesId, ignored -> new HashSet<>()).add(season);
                }
            }
        }
        seriesSeasonCounts = new HashMap<>();
        seriesSeasons.forEach((seriesId, seasons) -> seriesSeasonCounts.put(seriesId, seasons.size()));
        documentsById = new HashMap<>();
        Map<String, Integer> ordinalsById = new HashMap<>();
        for (int i = 0; i < this.documents.size(); i++) {
            SearchDocument document = this.documents.get(i);
            documentsById.put(document.id(), document);
            ordinalsById.put(document.id(), i);
        }
        // Tính trước vị trí tài liệu tập gốc, điểm êm dịu và cờ tập tổng hợp hoặc kịch tính cho bước lọc giờ đi ngủ và thời lượng ngắn.
        baseOrdinals = new int[this.documents.size()];
        calmScores = new int[this.documents.size()];
        compilationDocuments = new BitSet(this.documents.size());
        intenseDocuments = new BitSet(this.documents.size());
        for (int i = 0; i < this.documents.size(); i++) {
            SearchDocument document = this.documents.get(i);
            String episodeId = episodeId(document);
            baseOrdinals[i] = episodeId == null ? i : ordinalsById.getOrDefault(episodeId, i);
            if (document.itemType().equals(TYPE_EPISODE) || document.itemType().equals(TYPE_SERIES)) {
                var text = document.text();
                String foldedDetails = EpisodeMood.normalizeDetails(text.title(), text.description(),
                        text.keywords(), text.themes());
                calmScores[i] = EpisodeMood.calmScoreFolded(document.placement().seriesId(), foldedDetails);
                if (EpisodeMood.compilation(text.title(), text.keywords(), text.themes())) {
                    compilationDocuments.set(i);
                }
                if (EpisodeMood.intenseFolded(foldedDetails)) {
                    intenseDocuments.set(i);
                }
            }
        }
        // Tách từ, nhân trọng số trường cao và bigram (ưu tiên khớp cụm gấp 1.35 lần), lưu cả dạng có dấu lẫn không dấu rồi tính IDF cho BM25.
        genres = new ArrayList<>(this.documents.size());
        seriesNames = new HashMap<>();
        seriesDocuments.forEach(item -> seriesNames.put(item.id(), seriesNames(item)));
        namePostingsByLength = new HashMap<>();
        topicPostingsByLength = new HashMap<>();
        topicPhrases = new HashMap<>();
        lengthPenalties = new float[this.documents.size()];
        Map<String, List<Posting>> postings = new HashMap<>();
        long totalLength = 0;
        for (int index = 0; index < this.documents.size(); index++) {
            SearchDocument item = this.documents.get(index);
            genres.add(normalized(item.placement().genreTerms()));
            indexNames(item, index);

            var text = item.text();
            List<String> tokens = tokenize(text.documentText(item.placement()));
            lengthPenalties[index] = tokens.size();
            totalLength += tokens.size();
            Map<String, Integer> counts = new LinkedHashMap<>();
            addTerms(counts, tokens, 1);
            addTerms(counts, tokenize(text.highWeight(item.placement())), 2);
            addTerms(counts, tokenize(text.mediumWeight()), 1);
            addTerms(counts, bigrams(tokens), 2);
            for (var entry : new LinkedHashMap<>(counts).entrySet()) {
                String folded = normalize(entry.getKey());
                if (!folded.equals(entry.getKey())) {
                    counts.merge(folded, entry.getValue(), Integer::sum);
                }
            }
            int ordinal = index;
            counts.forEach((word, count) -> postings.computeIfAbsent(word, ignored -> new ArrayList<>())
                    .add(new Posting(ordinal, count)));
        }
        float average = totalLength == 0 ? 1 : (float) totalLength / this.documents.size();
        for (int index = 0; index < lengthPenalties.length; index++) {
            lengthPenalties[index] = 1.5f * (0.25f + 0.75f * lengthPenalties[index] / average);
        }
        terms = new HashMap<>();
        postings.forEach((word, posting) -> {
            double inverseFrequency = Math.log(1 + (this.documents.size() - posting.size() + 0.5) / (posting.size() + 0.5));
            double weight = word.contains("_") ? 1.35 : 1.0;
            terms.put(word, new Term((float) (weight * inverseFrequency * 2.5), List.copyOf(posting)));
        });
    }

    private void indexNames(SearchDocument item, int ordinal) {
        SearchDocument base = documents.get(baseOrdinals[ordinal]);
        List<List<String>> names = seriesNames.getOrDefault(seriesKey(base), List.of());
        names.forEach(name -> name.forEach(word -> addName(word, ordinal)));
        addNames(item.text().characters(), ordinal);
        if (base != item) {
            addNames(base.text().characters(), ordinal);
        }
        if (base.itemType().equals(TYPE_EPISODE)) {
            addNames(base.text().aliases().stream().filter(alias -> !Numbers.parse(alias).present()).toList(), ordinal);
        }
        if (item.itemType().equals(TYPE_EPISODE) || item.itemType().equals(TYPE_SERIES)) {
            List<List<String>> phrases = new ArrayList<>();
            addTopics(item.text().title(), ordinal, phrases);
            addTopics(item.text().synopsis(), ordinal, phrases);
            if (item.text().keywords() != null) {
                item.text().keywords().forEach(keyword -> addTopics(keyword, ordinal, phrases));
            }
            if (item.text().themes() != null) {
                item.text().themes().forEach(theme -> addTopics(theme, ordinal, phrases));
            }
            if (!phrases.isEmpty()) {
                topicPhrases.put(ordinal, List.copyOf(phrases));
            }
        }
    }

    private void addNames(List<String> names, int ordinal) {
        if (names != null) {
            names.forEach(name -> NameSimilarity.words(name).forEach(word -> addName(word, ordinal)));
        }
    }

    private void addName(String word, int ordinal) {
        int wordLength = word.codePointCount(0, word.length());
        if (wordLength >= 3) {
            namePostingsByLength.computeIfAbsent(wordLength, ignored -> new HashMap<>())
                    .computeIfAbsent(word, ignored -> new BitSet(documents.size())).set(ordinal);
        }
    }

    private void addTopics(String text, int ordinal, List<List<String>> phrases) {
        if (text != null && !text.isBlank()) {
            List<String> words = NameSimilarity.words(text);
            if (!words.isEmpty()) {
                phrases.add(words);
                for (String word : words) {
                    int wordLength = word.codePointCount(0, word.length());
                    if (wordLength >= 3) {
                        topicPostingsByLength.computeIfAbsent(wordLength, ignored -> new HashMap<>())
                                .computeIfAbsent(word, ignored -> new BitSet(documents.size())).set(ordinal);
                    }
                }
            }
        }
    }

    public int size() {
        return documents.size();
    }

    public List<Match> search(String query, int limit, SearchConstraints constraints) {
        return search(query, limit, constraints, id -> true);
    }

    // Luồng tìm kiếm chính: tách vế phủ định, lọc ứng viên hợp lệ, xếp hạng BM25 + sửa chính tả, tính RRF rồi gom mỗi tập tối đa một kết quả.
    public List<Match> search(String query, int limit, SearchConstraints constraints, Predicate<String> discoverable) {
        // Cắt câu thành vế chọn và vế loại trừ (nếu có) trước khi nhận diện số mùa/tập và tên bộ phim.
        if (query == null || UnicodeText.strip(query).isEmpty()) {
            return List.of();
        }
        String[] scopes = NEGATION.split(Normalizer.normalize(UnicodeText.caseFold(query), Normalizer.Form.NFC), 2);
        String positive = scopes[0];
        String negative = scopes.length == 2 ? scopes[1] : "";
        var requested = Numbers.parse(positive).overrideWith(constraints);
        boolean bedtime = constraints.preferShort() && EpisodeMood.bedtimeRequest(positive);
        Set<String> includedSeries = seriesIn(positive);
        BitSet allowed = allowed(negative, includedSeries, requested, constraints, bedtime, discoverable);
        if (allowed.isEmpty()) {
            return List.of();
        }

        // Chấm hạng ứng viên, quy đổi điểm RRF theo thứ bậc và gom trùng để mỗi tập hoặc bộ chỉ xuất hiện một lần.
        LexicalQuery words = LexicalQuery.from(positive);
        List<Integer> candidates = ranked(allowed, words, constraints, requested, bedtime, !includedSeries.isEmpty());
        Map<Integer, Double> fused = new LinkedHashMap<>();
        for (int rank = 0; rank < candidates.size(); rank++) {
            fused.put(candidates.get(rank), 1.0 / (RRF_K + rank));
        }
        List<Integer> distinctItems = new ArrayList<>();
        Set<String> used = new HashSet<>();
        int resultLimit = Math.clamp(limit, 1, 100);
        for (int index : preferTimecodes(candidates, positive)) {
            SearchDocument source = documents.get(index);
            String episodeId = episodeId(source);
            String id = episodeId == null ? source.id() : episodeId;
            if (used.add(id)) {
                distinctItems.add(index);
                if (!constraints.discovery() && distinctItems.size() >= resultLimit) {
                    break;
                }
            }
        }
        // Ở chế độ khám phá, xoay vòng từng bộ phim để danh sách gợi ý không bị dồn toàn tập của một series.
        var selected = constraints.discovery()
                ? DiverseSelection.roundRobin(distinctItems, index -> seriesKey(documents.get(index)), resultLimit)
                : distinctItems;
        return selected.stream().map(index -> {
            var source = documents.get(index);
            String episode = episodeId(source);
            return match(baseDocument(source), source, episode, fused.get(index),
                    episode != null && !discoverable.test(episode));
        }).toList();
    }

    private static String seriesKey(SearchDocument item) {
        var parent = item.placement().seriesId();
        return parent == null || parent.isBlank() ? item.id() : parent;
    }

    // Xếp hạng ứng viên: ưu tiên khớp chính xác rồi mới tới khớp mờ, cộng thêm ưu tiên êm dịu hoặc ngắn gọn nếu truy vấn yêu cầu.
    private List<Integer> ranked(BitSet allowed, LexicalQuery query, SearchConstraints constraints, Numbers requested,
            boolean bedtime, boolean namedSeries) {
        // Trường hợp câu hỏi chỉ có số mùa/tập hoặc đang ở chế độ khám phá nên không còn từ khóa chữ.
        int candidateLimit = constraints.discovery() ? documents.size() : CANDIDATE_LIMIT;
        var shortOrder = Comparator.<Integer>comparingInt(index -> bedtime ? -calmScores[baseOrdinals[index]] : 0)
                .thenComparing(index -> constraints.preferShort()
                        && !EpisodeRuntime.atMost(documents.get(baseOrdinals[index]).durationMs(), EpisodeRuntime.SHORT_MAX_MS));
        if (query.isEmpty()) {
            if (!(requested.present() || constraints.discovery())) {
                return List.of();
            }
            return allowed.stream().boxed().filter(index -> !constraints.discovery()
                            || documents.get(index).itemType().equals(TYPE_EPISODE)
                            || documents.get(index).itemType().equals(TYPE_SERIES))
                    .sorted(shortOrder.thenComparingInt(index -> index)).limit(candidateLimit).toList();
        }
        // Tính điểm BM25 cùng điểm cộng sửa chính tả, luôn xếp tài liệu khớp chính xác lên trước tài liệu khớp mờ.
        Relevance scores = scores(query, allowed);
        NameMatches corrections = nameMatches(query, allowed);
        BitSet exact = new BitSet(documents.size());
        for (int index = allowed.nextSetBit(0); index >= 0; index = allowed.nextSetBit(index + 1)) {
            if (query.relevant(scores.matches()[index], scores.scores()[index], namedSeries || scores.phrases().get(index))) {
                exact.set(index);
            }
        }
        var order = Comparator.<Integer, Boolean>comparing(index -> !exact.get(index))
                .thenComparing(shortOrder)
                .thenComparing(index -> constraints.preferEvidence() && documents.get(index).timecode().sourceStart() == null)
                .thenComparingDouble(index -> -scoreWithCorrections(scores, corrections, exact, index))
                .thenComparingInt(index -> index);
        return allowed.stream().boxed().filter(index -> exact.get(index) || (corrections.counts()[index] > 0
                        && (namedSeries || scores.matches()[index] + corrections.counts()[index] == query.distinctWords().size())
                        && query.relevant(scores.matches()[index] + corrections.counts()[index],
                                scoreWithCorrections(scores, corrections, exact, index), true)))
                .sorted(order)
                .limit(candidateLimit).toList();
    }

    private static float scoreWithCorrections(Relevance scores, NameMatches corrections, BitSet exact, int index) {
        return scores.scores()[index] + (exact.get(index) ? 0 : corrections.bonuses()[index]);
    }

    // Dò từ gõ sai 1 đến 2 ký tự trong các nhóm độ dài lân cận để không phải tính khoảng cách chỉnh sửa trên cả từ điển.
    private NameMatches nameMatches(LexicalQuery query, BitSet allowed) {
        int[] counts = new int[documents.size()];
        float[] bonuses = new float[documents.size()];
        Predicate<String> allowShortFuzzy = word -> !terms.containsKey(word);
        boolean hasLongQueryWord = query.distinctWords().stream()
                .anyMatch(word -> word.codePointCount(0, word.length()) >= 4);
        for (String word : query.distinctWords()) {
            int length = word.codePointCount(0, word.length());
            if (length < 3) {
                continue;
            }
            var near = new BitSet(documents.size());
            var farther = new BitSet(documents.size());
            int minNameLength = length == 3 ? 3 : Math.max(4, length - (length <= 7 ? 1 : 2));
            int maxNameLength = length == 3 ? 3 : length + (length <= 7 ? 1 : 2);
            for (int len = minNameLength; len <= maxNameLength; len++) {
                Map<String, BitSet> bucket = namePostingsByLength.get(len);
                if (bucket == null) {
                    continue;
                }
                bucket.forEach((name, indices) -> {
                    int distance = NameSimilarity.wordDistance(word, name, allowShortFuzzy);
                    if (distance == 1) {
                        near.or(indices);
                    } else if (distance == 2) {
                        farther.or(indices);
                    }
                });
            }
            // Nếu từ truy vấn không có trong từ điển chính xác, mở rộng tìm mờ sang tiêu đề/chủ đề rồi trừ bớt tài liệu đã khớp đúng.
            if (hasLongQueryWord && !terms.containsKey(word)) {
                addTopicWordMatches(query.words(), word, length, allowShortFuzzy, allowed, near, farther);
            }
            Term exact = terms.get(word);
            if (exact != null) {
                exact.postings().forEach(posting -> {
                    near.clear(posting.index());
                    farther.clear(posting.index());
                });
            }
            farther.andNot(near);
            addNameMatches(near, allowed, counts, bonuses, 1);
            addNameMatches(farther, allowed, counts, bonuses, 0.6f);
        }
        return new NameMatches(counts, bonuses);
    }

    private void addTopicWordMatches(List<String> queryWords, String word, int length,
            Predicate<String> allowShortFuzzy, BitSet allowed, BitSet near, BitSet farther) {
        List<List<String>> windows = adjacentPairs(queryWords, word);
        boolean allowShortDeletion = length == 3 && !windows.isEmpty();
        if (length == 3 && !allowShortDeletion) {
            return;
        }
        var candidateNear = new BitSet(documents.size());
        var candidateFarther = new BitSet(documents.size());
        int minTopicLength = length == 3 ? 3 : Math.max(4, length - (length <= 7 ? 1 : 2));
        int maxTopicLength = length == 3 ? (allowShortDeletion ? 4 : 3) : length + (length <= 7 ? 1 : 2);
        for (int len = minTopicLength; len <= maxTopicLength; len++) {
            Map<String, BitSet> bucket = topicPostingsByLength.get(len);
            if (bucket == null) {
                continue;
            }
            bucket.forEach((topic, indices) -> {
                int distance = NameSimilarity.topicWordDistance(word, topic, allowShortDeletion, allowShortFuzzy);
                if (distance == 1) {
                    candidateNear.or(indices);
                } else if (distance == 2) {
                    candidateFarther.or(indices);
                }
            });
        }
        candidateNear.and(allowed);
        candidateFarther.and(allowed);
        if (windows.isEmpty()) {
            near.or(candidateNear);
            farther.or(candidateFarther);
            return;
        }
        filterByTopicWindows(candidateNear, windows, allowShortFuzzy, near);
        filterByTopicWindows(candidateFarther, windows, allowShortFuzzy, farther);
    }

    private void filterByTopicWindows(BitSet candidates, List<List<String>> windows,
            Predicate<String> allowShortFuzzy, BitSet target) {
        for (int index = candidates.nextSetBit(0); index >= 0; index = candidates.nextSetBit(index + 1)) {
            List<List<String>> phrases = topicPhrases.get(index);
            if (phrases != null && matchesAnyTopicWindow(windows, phrases, allowShortFuzzy)) {
                target.set(index);
            }
        }
    }

    private static boolean matchesAnyTopicWindow(List<List<String>> windows, List<List<String>> phrases,
            Predicate<String> allowShortFuzzy) {
        for (List<String> window : windows) {
            for (List<String> phrase : phrases) {
                if (NameSimilarity.topicPhraseDistance(window, phrase, allowShortFuzzy) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<List<String>> adjacentPairs(List<String> words, String target) {
        List<List<String>> pairs = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            if (!words.get(i).equals(target)) {
                continue;
            }
            if (i > 0) {
                pairs.add(List.of(words.get(i - 1), target));
            }
            if (i + 1 < words.size()) {
                pairs.add(List.of(target, words.get(i + 1)));
            }
        }
        return pairs;
    }

    private static void addNameMatches(BitSet matches, BitSet allowed, int[] counts, float[] bonuses, float bonus) {
        matches.and(allowed);
        for (int index = matches.nextSetBit(0); index >= 0; index = matches.nextSetBit(index + 1)) {
            counts[index]++;
            bonuses[index] += bonus;
        }
    }

    // Dựng tập bit các tài liệu vượt qua hết điều kiện lọc (bộ/mùa/tập, phủ định, thể loại, năm, thời lượng và quyền phát).
    private BitSet allowed(String negative, Set<String> includedSeries, Numbers requested, SearchConstraints constraints,
            boolean bedtime, Predicate<String> discoverable) {
        // Nếu người dùng hỏi đúng tên bộ kèm số mùa/tập và cờ allowExternalLookup bật, cho phép giữ cả tập chỉ xem ngoài để chat báo rõ.
        Set<String> excludedSeries = seriesIn(negative);
        Numbers excluded = Numbers.parse(negative);
        Set<String> includedGenres = normalized(constraints.includeGenres());
        Set<String> excludedGenres = normalized(constraints.excludeGenres());
        BitSet allowed = new BitSet(documents.size());
        boolean playableOnly = constraints.discovery()
                && !(constraints.allowExternalLookup() && requested.present() && !includedSeries.isEmpty());
        if (constraints.seriesOnly() && includedSeries.isEmpty()) {
            return allowed;
        }
        for (int index = 0; index < documents.size(); index++) {
            SearchDocument item = documents.get(index);
            var placement = item.placement();
            boolean series = item.itemType().equals("series");
            String seriesId = series ? item.id() : placement.seriesId();
            if ((constraints.seriesOnly() && !series) || (constraints.episodesOnly() && series)
                    || (!includedSeries.isEmpty() && !includedSeries.contains(seriesId))
                    || !requested.matches(placement)) {
                continue;
            }
            if ((!excludedSeries.isEmpty() || excluded.present()) && excluded.matches(placement)
                    && (excludedSeries.isEmpty() || excludedSeries.contains(seriesId))) {
                continue;
            }
            if ((!includedGenres.isEmpty() && Collections.disjoint(includedGenres, genres.get(index)))
                    || !Collections.disjoint(excludedGenres, genres.get(index))) {
                continue;
            }
            Integer year = placement.year();
            if ((constraints.yearMin() != null && (year == null || year == 0 || year < constraints.yearMin()))
                    || (constraints.yearMax() != null && (year == null || year == 0 || year > constraints.yearMax()))) {
                continue;
            }
            String episodeId = episodeId(item);
            if (constraints.excludedItems().contains(episodeId == null ? item.id() : episodeId)) {
                continue;
            }
            if (playableOnly && episodeId != null && !discoverable.test(episodeId)) {
                continue;
            }
            if (constraints.maxDurationMs() != null && !withinRuntime(baseOrdinals[index], constraints, bedtime)) {
                continue;
            }
            allowed.set(index);
        }
        return allowed;
    }

    private boolean withinRuntime(int baseOrdinal, SearchConstraints constraints, boolean bedtime) {
        return EpisodeRuntime.atMost(documents.get(baseOrdinal).durationMs(), constraints.maxDurationMs())
                && (!constraints.preferShort() || !compilationDocuments.get(baseOrdinal))
                && (!bedtime || !intenseDocuments.get(baseOrdinal));
    }

    private Relevance scores(LexicalQuery query, BitSet allowed) {
        Set<String> queryTerms = new LinkedHashSet<>(query.words());
        queryTerms.addAll(bigrams(query.words()));
        float[] scores = new float[documents.size()];
        int[] matched = new int[documents.size()];
        BitSet phrases = new BitSet(documents.size());
        for (String word : queryTerms) {
            Term term = terms.get(word);
            if (term == null) {
                continue;
            }
            for (Posting posting : term.postings()) {
                int index = posting.index();
                if (allowed.get(index)) {
                    scores[index] += term.factor() * posting.count() / (posting.count() + lengthPenalties[index]);
                    if (query.distinctWords().contains(word)) {
                        matched[index]++;
                    } else {
                        phrases.set(index);
                    }
                }
            }
        }
        return new Relevance(scores, matched, phrases);
    }

    // Tra cứu thẳng bộ phim hoặc tập phim theo mã, gắn cờ externalOnly nếu tập không có tệp phát trực tiếp.
    public Optional<SearchHit> item(String id, Predicate<String> discoverable) {
        return Optional.ofNullable(documentsById.get(id))
                .filter(item -> item.itemType().equals("series") || item.itemType().equals("episode"))
                .map(item -> {
                    String episode = episodeId(item);
                    return match(item, item, episode, 1.0, episode != null && !discoverable.test(episode)).hit();
                });
    }

    // Dò phân đoạn kế tiếp trong cùng tập theo vị trí segmentId; nếu tài liệu trước là cảnh/lời thoại thì lùi về so mốc episodeStart.
    public Optional<SearchHit> nextSegment(String documentId, Predicate<String> discoverable) {
        SearchDocument previous = documentsById.get(documentId);
        if (previous == null || episodeId(previous) == null) {
            return Optional.empty();
        }
        String episode = episodeId(previous);
        boolean externalOnly = !discoverable.test(episode);
        var segments = segmentsByEpisode.getOrDefault(episode, List.of());
        String previousSegment = previous.itemType().equals("segment") ? previous.id() : previous.placement().segmentId();
        if (previousSegment != null) {
            for (int index = 0; index < segments.size(); index++) {
                if (segments.get(index).id().equals(previousSegment)) {
                    return index + 1 == segments.size() ? Optional.empty()
                            : Optional.of(segmentHit(segments.get(index + 1), externalOnly));
                }
            }
        }
        Long start = previous.timecode().episodeStart();
        return segments.stream().filter(item -> start == null
                        || (item.timecode().episodeStart() != null && item.timecode().episodeStart() > start))
                .findFirst().map(item -> segmentHit(item, externalOnly));
    }

    private SearchDocument baseDocument(SearchDocument item) {
        return documentsById.getOrDefault(episodeId(item), item);
    }

    private SearchHit segmentHit(SearchDocument source, boolean externalOnly) {
        return match(baseDocument(source), source, episodeId(source), 1.0, externalOnly).hit();
    }

    public List<String> availableSeriesTitles() {
        return cachedSeriesTitles;
    }

    // Gợi ý các bộ phim khác gần nhất với bộ phim trong câu hỏi, ưu tiên trùng chủ đề (trọng số 2) hơn trùng thể loại (trọng số 1).
    public List<SearchHit> similarSeries(String query, int limit) {
        var reference = seriesIn(query);
        if (reference.isEmpty()) {
            return List.of();
        }
        var anchors = seriesDocuments.stream().filter(item -> reference.contains(item.id()))
                .map(item -> seriesTags.get(item.id())).toList();
        return seriesDocuments.stream().filter(item -> !reference.contains(item.id()))
                .map(item -> new Similarity(item, anchors.stream()
                        .mapToInt(anchor -> anchor.similarity(seriesTags.get(item.id()))).max().orElse(0)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(Similarity::score).reversed().thenComparing(item -> item.item().id()))
                .limit(limit).map(item -> match(item.item(), item.item(), null, item.score(), false).hit()).toList();
    }

    // Khi người dùng hỏi mốc phút/giây, đẩy các cảnh hoặc lời thoại khớp cụm từ lên đầu trong cùng một tập để trả đúng timecode.
    private List<Integer> preferTimecodes(List<Integer> candidates, String query) {
        var request = TIMECODE.matcher(normalize(query));
        if (!request.find()) {
            return candidates;
        }
        Set<String> phrases = new HashSet<>(bigrams(tokenize(request.replaceAll(" "))));
        if (phrases.isEmpty()) {
            return candidates;
        }
        Map<String, List<Integer>> episodes = new LinkedHashMap<>();
        for (int index : candidates) {
            SearchDocument document = documents.get(index);
            String episode = episodeId(document);
            episodes.computeIfAbsent(episode == null ? document.id() : episode, ignored -> new ArrayList<>()).add(index);
        }
        BitSet matching = new BitSet(documents.size());
        for (int index : candidates) {
            SearchDocument document = documents.get(index);
            if (document.timecode().episodeStart() != null && !Collections.disjoint(phrases,
                    bigrams(tokenize(normalize(document.text().evidenceText()))))) {
                matching.set(index);
            }
        }
        return episodes.values().stream()
                .flatMap(indices -> indices.stream().sorted(Comparator.comparing(index -> !matching.get(index))))
                .toList();
    }

    private Set<String> seriesIn(String query) {
        var words = NameSimilarity.words(query);
        Predicate<String> allowShortFuzzy = word -> !terms.containsKey(word);
        Map<String, Integer> matches = new HashMap<>();
        seriesNames.forEach((id, names) -> names.stream().filter(name -> !name.isEmpty() && name.size() <= words.size())
                .mapToInt(name -> NameSimilarity.phraseDistance(words, name, allowShortFuzzy))
                .filter(distance -> distance >= 0)
                .min().ifPresent(distance -> matches.put(id, distance)));
        int closest = matches.values().stream().mapToInt(Integer::intValue).min().orElse(-1);
        return matches.entrySet().stream().filter(entry -> entry.getValue() == closest)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private static List<List<String>> seriesNames(SearchDocument item) {
        List<String> names = new ArrayList<>(item.text().aliases());
        names.addAll(SeriesSearchAliases.forSeries(item.id()));
        if (item.text().title() != null) {
            names.addFirst(item.text().title());
        }
        return names.stream().map(NameSimilarity::words).filter(name -> !name.isEmpty()).distinct().toList();
    }

    private Match match(SearchDocument base, SearchDocument source, String episodeId, double score, boolean externalOnly) {
        var position = source.timecode();
        var placement = base.placement();
        var text = base.text();
        String evidence = source.text().evidenceText();
        int length = Math.min(MAX_EVIDENCE_LENGTH, evidence.codePointCount(0, evidence.length()));
        String timeline = source.mediaAssetId() != null && position.sourceStart() != null ? TIMELINE_FILE
                : TYPE_SEGMENT.equals(source.itemType()) && position.episodeStart() != null ? TIMELINE_EPISODE : null;
        var excerpt = new SearchEvidence(source.id(), evidence.substring(0, evidence.offsetByCodePoints(0, length)),
                position.episodeStart(), position.episodeEnd(), source.itemType(), timeline, source.mediaAssetId(),
                position.sourceStart(), position.sourceEnd());
        var hit = new SearchHit(base.id(), text.title(), placement.year(), DEFAULT_RATING,
                base.runtime(), placement.genres(), text.description(), null, null, null, null, base.itemType(),
                placement.seriesId(), placement.seasonId(), placement.episodeId(), placement.segmentId(),
                placement.episodeNumber(), placement.airDate(), base.durationMs(), position.sourceStart(),
                position.sourceEnd(), base.primaryMediaAssetId(), base.mediaAssetId(), text.aliases(), text.characterNames(),
                text.plotFacts(), text.keywords(), text.themes(), text.visualSummary(), text.dialogueSummary(),
                text.soundEvents(), placement.seriesTitle(), placement.episodeTitle(), base.comparativeAttributes(),
                DIAGNOSTICS, episodeId, position.episodeStart(), score, null, List.of(excerpt),
                seriesCount(base, seriesEpisodeCounts), seriesCount(base, seriesSeasonCounts), externalOnly,
                RatingSummary.EMPTY);
        return new Match(hit, source.mediaAssetId(), placement.genreLabels());
    }

    private static Integer seriesCount(SearchDocument item, Map<String, Integer> counts) {
        return item.itemType().equals("series") ? counts.getOrDefault(item.id(), 0) : null;
    }

    private static String episodeId(SearchDocument source) {
        String episode = source.placement().episodeId();
        if (episode != null && !episode.isEmpty()) {
            return episode;
        }
        return source.itemType().equals("episode") ? source.id() : null;
    }

    private static Set<String> normalized(List<String> values) {
        return values == null ? Set.of() : values.stream().map(LexicalIndex::normalize).collect(Collectors.toUnmodifiableSet());
    }

    // Chuyển chữ thường theo chuẩn Unicode rồi bỏ dấu tiếng Việt (đổi riêng chữ đ sang d vì NFD không tách được đ).
    public static String normalize(String text) {
        String decomposed = Normalizer.normalize(UnicodeText.caseFold(text).replace('đ', 'd'), Normalizer.Form.NFD);
        return MARK.matcher(decomposed).replaceAll("");
    }

    // Đưa về NFC trước khi cắt token chữ và số để giữ khớp với quy tắc tách từ cũ của bản Python.
    public static List<String> tokenize(String text) {
        String folded = Normalizer.normalize(UnicodeText.caseFold(text), Normalizer.Form.NFC);
        return TOKEN.matcher(folded).results().map(match -> match.group()).toList();
    }

    private static List<String> bigrams(List<String> tokens) {
        List<String> bigrams = new ArrayList<>(tokens.size());
        for (int index = 1; index < tokens.size(); index++) {
            bigrams.add(tokens.get(index - 1) + "_" + tokens.get(index));
        }
        return bigrams;
    }

    private static void addTerms(Map<String, Integer> counts, List<String> tokens, int weight) {
        tokens.forEach(token -> counts.merge(token, weight, Integer::sum));
    }

    private static Pattern bounded(String expression) {
        return Pattern.compile("(?<!" + WORD + ")" + expression + "(?!" + WORD + ")");
    }

    private static Pattern numbered(String names) {
        return bounded("(?:" + names + ")" + NUMBER_PREFIX + NUMBER);
    }

    private static Long parsedNumber(String digits) {
        long number = 0;
        for (int offset = 0; offset < digits.length();) {
            int point = digits.codePointAt(offset);
            number = number * 10 + Character.digit(point, 10);
            if (number > Integer.MAX_VALUE) {
                return number;
            }
            offset += Character.charCount(point);
        }
        return number;
    }

    // Số mùa và số tập rút ra từ câu hỏi (dạng S01E02 hoặc "mùa X tập Y") hay ghi đè từ bộ lọc.
    private record Numbers(Long season, Long episode) {

        static Numbers parse(String query) {
            String text = normalize(query);
            var combined = COMBINED.matcher(text);
            if (combined.find()) {
                return new Numbers(parsedNumber(combined.group(1)), parsedNumber(combined.group(2)));
            }
            var season = SEASON.matcher(text);
            var episode = EPISODE.matcher(text);
            return new Numbers(season.find() ? parsedNumber(season.group(1)) : null,
                    episode.find() ? parsedNumber(episode.group(1)) : null);
        }

        Numbers overrideWith(SearchConstraints constraints) {
            return new Numbers(override(season, constraints.seasonNumber()), override(episode, constraints.episodeNumber()));
        }

        private static Long override(Long inferred, Integer constraint) {
            return constraint == null ? inferred : Long.valueOf(constraint.longValue());
        }

        boolean present() {
            return season != null || episode != null;
        }

        boolean matches(SearchDocument.Placement placement) {
            return matches(season, placement.seasonNumber()) && matches(episode, placement.episodeNumber());
        }

        private static boolean matches(Long number, Integer actual) {
            return number == null || (actual != null && number.longValue() == actual.longValue());
        }
    }

    // Mục từ trong chỉ mục ngược kèm hệ số IDF đã nhân sẵn trọng số.
    private record Term(float factor, List<Posting> postings) {
    }

    // Số lần xuất hiện của một từ tại vị trí tài liệu tương ứng.
    private record Posting(int index, int count) {
    }

    // Mảng điểm BM25, số từ khớp và bit cờ khớp cụm từ cho toàn bộ tài liệu trong một lần truy vấn.
    private record Relevance(float[] scores, int[] matches, BitSet phrases) {
    }

    // Số từ khớp mờ và điểm cộng thêm của từng tài liệu khi người dùng gõ sai chính tả.
    private record NameMatches(int[] counts, float[] bonuses) {
    }

    // Tập thể loại và chủ đề đã bỏ dấu để tính điểm gần nhau giữa hai bộ phim.
    private record SeriesTags(Set<String> genres, Set<String> themes) {

        int similarity(SeriesTags other) {
            if (other == null) {
                return 0;
            }
            int genreOverlap = (int) genres.stream().filter(other.genres::contains).count();
            int themeOverlap = (int) themes.stream().filter(other.themes::contains).count();
            return genreOverlap + themeOverlap * 2;
        }
    }

    // Cặp bộ phim và điểm tương đồng dùng để sắp xếp danh sách gợi ý.
    private record Similarity(SearchDocument item, int score) {
    }

    // Kết quả khớp hoàn chỉnh mang thêm mã tệp bằng chứng và nhãn thể loại tiếng Việt cho DiscoverResponse.
    public record Match(SearchHit hit, String evidenceMediaAssetId, List<String> genreLabels) {
    }
}
