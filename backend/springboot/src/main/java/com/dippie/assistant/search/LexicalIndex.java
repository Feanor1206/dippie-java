package com.dippie.assistant.search;

import com.dippie.common.text.UnicodeText;

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
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class LexicalIndex {

    private static final int CANDIDATE_LIMIT = 30;
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
    private static final Map<String, Object> DIAGNOSTICS = Map.of("retrieval", "bm25", "denseReady", false,
            "missingEmbeddings", 0, "calibrated", false);

    private final List<SearchDocument> documents;
    private final Map<String, SearchDocument> documentsById;
    private final Map<String, Term> terms;
    private final float[] lengthPenalties;
    private final List<Set<String>> genres;
    private final Map<String, List<String>> seriesNames;
    private final Map<String, Integer> seriesEpisodeCounts;
    private final Map<String, Integer> seriesSeasonCounts;

    LexicalIndex(List<SearchDocument> documents) {
        this.documents = List.copyOf(documents);
        seriesEpisodeCounts = new HashMap<>();
        Map<String, Set<Integer>> seriesSeasons = new HashMap<>();
        for (SearchDocument item : documents) {
            if (item.itemType().equals("episode")) {
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
        genres = new ArrayList<>(documents.size());
        seriesNames = new HashMap<>();
        lengthPenalties = new float[documents.size()];
        Map<String, List<Posting>> postings = new HashMap<>();
        long totalLength = 0;
        for (int index = 0; index < documents.size(); index++) {
            SearchDocument item = documents.get(index);
            documentsById.put(item.id(), item);
            genres.add(normalized(item.placement().genres()));
            if (item.itemType().equals("series")) {
                seriesNames.put(item.id(), seriesNames(item.text()));
            }

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
        float average = totalLength == 0 ? 1 : (float) totalLength / documents.size();
        for (int index = 0; index < lengthPenalties.length; index++) {
            lengthPenalties[index] = 1.5f * (0.25f + 0.75f * lengthPenalties[index] / average);
        }
        terms = new HashMap<>();
        postings.forEach((word, posting) -> {
            double inverseFrequency = Math.log(1 + (documents.size() - posting.size() + 0.5) / (posting.size() + 0.5));
            double weight = word.contains("_") ? 1.35 : 1.0;
            terms.put(word, new Term((float) (weight * inverseFrequency * 2.5), List.copyOf(posting)));
        });
    }

    int size() {
        return documents.size();
    }

    List<Match> search(String query, int limit, SearchConstraints constraints) {
        if (query == null || UnicodeText.strip(query).isEmpty()) {
            return List.of();
        }
        String[] scopes = NEGATION.split(Normalizer.normalize(UnicodeText.caseFold(query), Normalizer.Form.NFC), 2);
        String positive = scopes[0];
        String negative = scopes.length == 2 ? scopes[1] : "";
        var requested = Numbers.parse(positive).overrideWith(constraints);
        BitSet allowed = allowed(positive, negative, requested, constraints);
        if (allowed.isEmpty()) {
            return List.of();
        }

        float[] scores = scores(positive, allowed);
        List<Integer> candidates = allowed.stream().boxed().filter(index -> scores[index] > 0)
                .sorted(Comparator.<Integer>comparingDouble(index -> -scores[index]).thenComparingInt(index -> index))
                .limit(CANDIDATE_LIMIT).toList();
        if (candidates.isEmpty() && requested.present()) {
            candidates = allowed.stream().limit(CANDIDATE_LIMIT).boxed().toList();
        }
        Map<Integer, Double> fused = new LinkedHashMap<>();
        for (int rank = 0; rank < candidates.size(); rank++) {
            fused.put(candidates.get(rank), 1.0 / (61 + rank));
        }
        List<Match> results = new ArrayList<>();
        Set<String> used = new HashSet<>();
        int resultLimit = Math.clamp(limit, 1, 100);
        for (int index : preferTimecodes(candidates, positive)) {
            SearchDocument source = documents.get(index);
            String episodeId = episodeId(source);
            String id = episodeId == null ? source.id() : episodeId;
            if (used.add(id)) {
                results.add(match(documentsById.getOrDefault(id, source), source, episodeId, fused.get(index)));
                if (results.size() >= resultLimit) {
                    break;
                }
            }
        }
        return List.copyOf(results);
    }

    private BitSet allowed(String positive, String negative, Numbers requested, SearchConstraints constraints) {
        Set<String> includedSeries = seriesIn(positive);
        Set<String> excludedSeries = seriesIn(negative);
        Numbers excluded = Numbers.parse(negative);
        Set<String> includedGenres = normalized(constraints.includeGenres());
        Set<String> excludedGenres = normalized(constraints.excludeGenres());
        BitSet allowed = new BitSet(documents.size());
        if (constraints.seriesOnly() && includedSeries.isEmpty()) {
            return allowed;
        }
        for (int index = 0; index < documents.size(); index++) {
            SearchDocument item = documents.get(index);
            var placement = item.placement();
            boolean series = item.itemType().equals("series");
            String seriesId = series ? item.id() : placement.seriesId();
            if ((constraints.seriesOnly() && !series) || (!includedSeries.isEmpty() && !includedSeries.contains(seriesId))
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
            allowed.set(index);
        }
        return allowed;
    }

    private float[] scores(String query, BitSet allowed) {
        List<String> tokens = tokenize(query);
        Set<String> queryTerms = new LinkedHashSet<>(tokens);
        queryTerms.addAll(bigrams(tokens));
        float[] scores = new float[documents.size()];
        for (String word : queryTerms) {
            Term term = terms.get(word);
            if (term == null) {
                continue;
            }
            for (Posting posting : term.postings()) {
                int index = posting.index();
                if (allowed.get(index)) {
                    scores[index] += term.factor() * posting.count() / (posting.count() + lengthPenalties[index]);
                }
            }
        }
        return scores;
    }

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
        String words = " " + String.join(" ", tokenize(normalize(query))) + " ";
        return seriesNames.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(words::contains))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private static List<String> seriesNames(SearchDocument.Text text) {
        List<String> names = new ArrayList<>(text.aliases());
        if (text.title() != null) {
            names.addFirst(text.title());
        }
        return names.stream().filter(name -> !tokenize(name).isEmpty())
                .map(name -> " " + String.join(" ", tokenize(normalize(name))) + " ").toList();
    }

    private Match match(SearchDocument base, SearchDocument source, String episodeId, double score) {
        var position = source.timecode();
        var placement = base.placement();
        var text = base.text();
        String evidence = source.text().evidenceText();
        int length = Math.min(1200, evidence.codePointCount(0, evidence.length()));
        var excerpt = new SearchEvidence(source.id(), evidence.substring(0, evidence.offsetByCodePoints(0, length)),
                position.episodeStart(), position.episodeEnd(), source.itemType());
        var hit = new SearchHit(base.id(), text.title(), placement.year() == null ? 0 : placement.year(), 8.0,
                base.runtime(), placement.genres(), text.description(), null, null, null, null, base.itemType(),
                placement.seriesId(), placement.seasonId(), placement.episodeId(), placement.segmentId(),
                placement.episodeNumber(), placement.airDate(), base.durationMs(), position.sourceStart(),
                position.sourceEnd(), base.primaryMediaAssetId(), base.mediaAssetId(), text.aliases(), text.characters(),
                text.plotFacts(), text.keywords(), text.themes(), text.visualSummary(), text.dialogueSummary(),
                text.soundEvents(), placement.seriesTitle(), placement.episodeTitle(), base.comparativeAttributes(),
                DIAGNOSTICS, episodeId, position.episodeStart(), score, null, List.of(excerpt),
                seriesCount(base, seriesEpisodeCounts), seriesCount(base, seriesSeasonCounts));
        return new Match(hit, source.mediaAssetId());
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
        return values.stream().map(LexicalIndex::normalize).collect(Collectors.toSet());
    }

    static String normalize(String text) {
        String decomposed = Normalizer.normalize(UnicodeText.caseFold(text).replace('đ', 'd'), Normalizer.Form.NFD);
        return MARK.matcher(decomposed).replaceAll("");
    }

    static List<String> tokenize(String text) {
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

    private record Term(float factor, List<Posting> postings) {
    }

    private record Posting(int index, int count) {
    }

    record Match(SearchHit hit, String evidenceMediaAssetId) {
    }
}
