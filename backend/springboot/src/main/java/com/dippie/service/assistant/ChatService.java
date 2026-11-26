package com.dippie.service.assistant;

import com.dippie.common.rating.RatingLookup;
import com.dippie.dto.assistant.ChatDonePayload;
import com.dippie.dto.assistant.ChatEvent;
import com.dippie.dto.assistant.ChatMetadata;
import com.dippie.exception.ChatCapacityException;
import com.dippie.exception.TooManyRequestsException;
import com.dippie.model.assistant.ChatHistoryMessage;
import com.dippie.model.assistant.ChatIntent;
import com.dippie.model.assistant.ChatPipelineStages;
import com.dippie.model.assistant.SearchHit;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

// Facade ghép các bước của một lượt chat: phân tích ý định, tìm kho phim, gắn đánh giá rồi phát luồng ChatEvent.
public class ChatService {

    private static final String RETRIEVAL_STAGE = "java-lexical";
    private static final String TEMPLATE_STAGE = "template";
    private static final String CAPACITY_ERROR_TEXT = "CineBot đang hơi quá tải, bạn thử lại sau chút nhé.";
    private static final String UNAVAILABLE_ERROR_TEXT = "CineBot đang tạm gián đoạn, bạn thử tìm trực tiếp trong Kho phim nhé.";

    private final CatalogSearch catalogSearch;
    private final IntentParser intentParser;
    private final AnswerGenerator answerGenerator;
    private final RatingLookup ratings;
    private final ChatRetrieval retrieval;
    private final TemplateAnswerGenerator catalogAnswers;

    public ChatService(CatalogSearch catalogSearch, IntentParser intentParser, AnswerGenerator answerGenerator,
            RatingLookup ratings) {
        this(catalogSearch, intentParser, answerGenerator,
                new TemplateAnswerGenerator(catalogSearch::availableSeriesTitles), ratings);
    }

    public ChatService(CatalogSearch catalogSearch, IntentParser intentParser, AnswerGenerator answerGenerator,
            TemplateAnswerGenerator catalogAnswers, RatingLookup ratings) {
        this.catalogSearch = catalogSearch;
        this.intentParser = intentParser;
        this.answerGenerator = answerGenerator;
        this.ratings = ratings;
        this.retrieval = new ChatRetrieval(catalogSearch);
        this.catalogAnswers = catalogAnswers;
    }

    public boolean isReady() {
        return catalogSearch.isReady();
    }

    // Trả về Stream lười phát đúng thứ tự Metadata, các Token rồi Done (hoặc một sự kiện Error duy nhất nếu bước tìm phim lỗi).
    public Stream<ChatEvent> stream(String query, List<ChatHistoryMessage> history) {
        ChatIntent intent = intentParser.parse(query, history);
        Optional<ChatFollowUp> followUp = ChatFollowUp.parse(query);
        boolean asksForAlternate = ChatFollowUp.asksForAlternate(query);
        List<SearchHit> results;
        ChatRetrieval.Selection selection;
        try {
            selection = retrieval.find(intent, query, history, followUp, asksForAlternate);
            results = ratings.withSummaries(selection.hits(), SearchHit::id, SearchHit::withRatings);
        } catch (ChatCapacityException | TooManyRequestsException exception) {
            return Stream.of(new ChatEvent.Error(CAPACITY_ERROR_TEXT));
        } catch (RuntimeException exception) {
            return Stream.of(new ChatEvent.Error(UNAVAILABLE_ERROR_TEXT));
        }

        // Các ca nhiều kết quả, gợi ý chung, tóm tắt, hỏi nối tiếp hay tập chỉ xem YouTube mình cho đi thẳng qua mẫu chuẩn để khỏi gọi LLM tốn thời gian.
        boolean suppressFollowUps = !results.isEmpty()
                && (intent.moodRecommendation() || intent.seriesInfo() != null);
        List<String> followUps = suppressFollowUps ? List.of() : FollowUpSuggestions.build(results, selection.hasAlternatives());
        boolean exhausted = results.isEmpty() && asksForAlternate;
        ChatPipelineStages initialStages = new ChatPipelineStages(RETRIEVAL_STAGE, TEMPLATE_STAGE, true);
        ChatMetadata metadata = new ChatMetadata(results, followUps, intent, initialStages);
        AnswerGenerator generator = results.size() != 1 || "recommend".equals(intent.mode())
                || intent.summaryRequested() || followUp.isPresent()
                || results.getFirst().externalOnly() ? catalogAnswers : answerGenerator;

        AtomicReference<String> conversationStage = new AtomicReference<>(TEMPLATE_STAGE);
        AtomicReference<Stream<String>> openTokenStream = new AtomicReference<>();

        // Tự lái Spliterator 3 pha (0: metadata, 1: token, 2: done) để luồng sinh chữ chỉ mở khi controller bắt đầu đọc và luôn đóng trong onClose.
        Spliterator<ChatEvent> spliterator = new Spliterators.AbstractSpliterator<>(
                Long.MAX_VALUE,
                Spliterator.ORDERED | Spliterator.NONNULL
        ) {
            private int phase = 0;
            private Iterator<String> tokenIterator;
            private final StringBuilder fullText = new StringBuilder();

            @Override
            public boolean tryAdvance(Consumer<? super ChatEvent> action) {
                if (phase == 0) {
                    phase = 1;
                    action.accept(new ChatEvent.Metadata(metadata));
                    return true;
                }
                if (phase == 1) {
                    if (tokenIterator == null) {
                        Stream<String> stream = exhausted ? Stream.of(CatalogReplies.NO_MORE_EPISODES)
                                : generator.answer(intent, results, conversationStage::set);
                        openTokenStream.set(stream);
                        tokenIterator = stream.iterator();
                    }
                    if (tokenIterator.hasNext()) {
                        String token = tokenIterator.next();
                        fullText.append(token);
                        action.accept(new ChatEvent.Token(token));
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
                            fullText.toString(),
                            results,
                            followUps,
                            intent,
                            finalStages
                    );
                    action.accept(new ChatEvent.Done(done));
                    return true;
                }
                return false;
            }
        };

        return StreamSupport.stream(spliterator, false)
                .onClose(() -> closeQuietly(openTokenStream.getAndSet(null)));
    }

    private static void closeQuietly(Stream<String> stream) {
        if (stream != null) {
            stream.close();
        }
    }
}
