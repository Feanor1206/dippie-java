package com.dippie.assistant.chat;

import com.dippie.assistant.search.FollowUpSuggestions;
import com.dippie.assistant.search.CatalogSearch;
import com.dippie.assistant.search.SearchHit;
import com.dippie.common.error.TooManyRequestsException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

class ChatService {

    private static final int MOOD_SEARCH_LIMIT = 100;
    private static final String RETRIEVAL_STAGE = "java-lexical";
    private static final String CAPACITY_ERROR_TEXT = "CineBot đang bận. Thử lại sau.";
    private static final String UNAVAILABLE_ERROR_TEXT = "CineBot hiện chưa sẵn sàng. Bạn có thể tìm trong Kho phim.";

    private static final Pattern EPISODE_REQUEST_RE = Pattern.compile(
            "\\b(?:tap|episode)\\b",
            Pattern.UNICODE_CHARACTER_CLASS
    );
    private static final Pattern RUNTIME_MINUTES_RE = Pattern.compile("(\\d+)m");

    private final CatalogSearch catalogSearch;
    private final IntentParser intentParser;
    private final AnswerGenerator answerGenerator;

    ChatService(CatalogSearch catalogSearch, IntentParser intentParser, AnswerGenerator answerGenerator) {
        this.catalogSearch = catalogSearch;
        this.intentParser = intentParser;
        this.answerGenerator = answerGenerator;
    }

    boolean isReady() {
        return catalogSearch.isReady();
    }

    Stream<ChatEvent> stream(String query, List<ChatHistoryMessage> history) {
        ChatIntent intent = intentParser.parse(query, history);
        List<SearchHit> results;
        try {
            results = retrieveResults(intent);
        } catch (ChatCapacityException | TooManyRequestsException exception) {
            return Stream.of(new ChatEvent("error", Map.of("text", CAPACITY_ERROR_TEXT)));
        } catch (RuntimeException exception) {
            return Stream.of(new ChatEvent("error", Map.of("text", UNAVAILABLE_ERROR_TEXT)));
        }

        boolean suppressFollowUps = !results.isEmpty()
                && (intent.moodRecommendation() || intent.seriesInfo() != null);
        List<String> followUps = suppressFollowUps ? List.of() : FollowUpSuggestions.build(results);
        ChatPipelineStages initialStages = new ChatPipelineStages(RETRIEVAL_STAGE, "template", true);
        ChatMetadata metadata = new ChatMetadata(results, followUps, intent, initialStages);

        AtomicReference<String> conversationStage = new AtomicReference<>("template");
        AtomicReference<Stream<String>> openTokenStream = new AtomicReference<>();

        Spliterator<ChatEvent> spliterator = new Spliterators.AbstractSpliterator<>(
                Long.MAX_VALUE,
                Spliterator.ORDERED | Spliterator.NONNULL
        ) {
            private int phase = 0;
            private Iterator<String> tokenIterator;
            private final StringBuilder accumulatedText = new StringBuilder();

            @Override
            public boolean tryAdvance(Consumer<? super ChatEvent> action) {
                if (phase == 0) {
                    phase = 1;
                    action.accept(new ChatEvent("metadata", metadata));
                    return true;
                }
                if (phase == 1) {
                    if (tokenIterator == null) {
                        Stream<String> stream = answerGenerator.answer(intent, results, conversationStage::set);
                        openTokenStream.set(stream);
                        tokenIterator = stream.iterator();
                    }
                    if (tokenIterator.hasNext()) {
                        String token = tokenIterator.next();
                        accumulatedText.append(token);
                        action.accept(new ChatEvent("token", Map.of("text", token)));
                        return true;
                    }
                    closeQuietly(openTokenStream.getAndSet(null));
                    phase = 2;
                }
                if (phase == 2) {
                    phase = 3;
                    ChatPipelineStages finalStages = new ChatPipelineStages(
                            RETRIEVAL_STAGE,
                            conversationStage.get(),
                            true
                    );
                    ChatDonePayload done = new ChatDonePayload(
                            accumulatedText.toString(),
                            results,
                            followUps,
                            intent,
                            finalStages
                    );
                    action.accept(new ChatEvent("done", done));
                    return true;
                }
                return false;
            }
        };

        return StreamSupport.stream(spliterator, false)
                .onClose(() -> closeQuietly(openTokenStream.getAndSet(null)));
    }

    private List<SearchHit> retrieveResults(ChatIntent intent) {
        if ("chitchat".equals(intent.mode())) {
            return List.of();
        }
        String searchQuery = intent.semanticQuery();
        int limit = intent.requestedK();
        if (intent.moodRecommendation()) {
            searchQuery = searchQuery + ("sad".equals(intent.moodTarget())
                    ? " buồn bi kịch sad"
                    : " hài hước vui nhộn comedy");
            limit = MOOD_SEARCH_LIMIT;
        }
        List<SearchHit> raw = catalogSearch.search(searchQuery, limit, intent.toSearchConstraints());
        List<SearchHit> safeRaw = raw == null ? List.of() : raw;
        if (!intent.moodRecommendation()) {
            return List.copyOf(safeRaw);
        }
        return rankMoodResults(safeRaw, intent);
    }

    private List<SearchHit> rankMoodResults(List<SearchHit> raw, ChatIntent intent) {
        boolean episodeRequest = EPISODE_REQUEST_RE.matcher(TextFolding.fold(intent.semanticQuery())).find();
        List<RankedHit> ranked = new ArrayList<>();
        for (int index = 0; index < raw.size(); index++) {
            SearchHit item = raw.get(index);
            if (item == null) {
                continue;
            }
            if (episodeRequest && "series".equals(item.itemType())) {
                continue;
            }
            int score = MoodEvidence.score(item, intent.moodTarget());
            if (score <= 0) {
                continue;
            }
            boolean longEpisode = episodeRequest && isLongRuntime(item.runtime());
            ranked.add(new RankedHit(longEpisode, score, index, item));
        }
        ranked.sort(Comparator.comparing(RankedHit::longEpisode)
                .thenComparing(Comparator.comparingInt(RankedHit::score).reversed())
                .thenComparingInt(RankedHit::index));

        int maxResults = Math.min(intent.requestedK(), ranked.size());
        List<SearchHit> top = new ArrayList<>(maxResults);
        for (int i = 0; i < maxResults; i++) {
            top.add(ranked.get(i).hit());
        }
        return List.copyOf(top);
    }

    private static boolean isLongRuntime(String runtime) {
        if (runtime == null) {
            return false;
        }
        Matcher matcher = RUNTIME_MINUTES_RE.matcher(runtime);
        if (!matcher.matches()) {
            return false;
        }
        try {
            return Integer.parseInt(matcher.group(1)) > 90;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static void closeQuietly(Stream<String> stream) {
        if (stream != null) {
            stream.close();
        }
    }

    private record RankedHit(boolean longEpisode, int score, int index, SearchHit hit) {
    }
}
