package com.dippie.assistant.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.dippie.assistant.search.CatalogSearch;
import com.dippie.assistant.search.SearchConstraints;
import com.dippie.assistant.search.SearchEvidence;
import com.dippie.assistant.search.SearchHit;
import com.dippie.library.MovieRatingRepository;
import com.dippie.library.RatingSummaries;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

// Kiểm tra ChatService điều phối chào hỏi, xếp hạng theo tâm trạng, nhánh báo lỗi và trạng thái conversation trong sự kiện Done.
class ChatServiceTest {

    private final IntentParser intentParser = new IntentParser();
    private final RatingSummaries ratings = new RatingSummaries(mock(MovieRatingRepository.class));

    @Test
    void chitchatSkipsCatalogSearchAndEmitsMetadataTokensDone() {
        AtomicInteger searchCalls = new AtomicInteger();
        CatalogSearch search = stubSearch((query, limit, constraints) -> {
            searchCalls.incrementAndGet();
            return List.of();
        });

        ChatService service = new ChatService(search, intentParser, new TemplateAnswerGenerator(), ratings);
        List<ChatEvent> events = service.stream("Xin chào!", List.of()).toList();

        assertThat(searchCalls.get()).isZero();
        assertThat(events.getFirst().type()).isEqualTo("metadata");
        assertThat(events.getLast().type()).isEqualTo("done");

        ChatDonePayload done = (ChatDonePayload) events.getLast().data();
        assertThat(done.text()).isEqualTo("Mình là CineBot. Bạn cứ kể lại cảnh phim, nhân vật hoặc tập muốn tìm nhé.");
        assertThat(done.recommendedMovies()).isEmpty();
        assertThat(done.followUps()).containsExactly("Gợi ý tập phim nổi bật", "Tìm phim hài gia đình");
        assertThat(done.pipelineStages()).isEqualTo(new ChatPipelineStages("java-lexical", "template", true));
    }

    @Test
    void moodFlowKeepsTheQueryTermsAndNamesTheRankedEpisodes() {
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        AtomicInteger capturedLimit = new AtomicInteger();

        SearchHit seriesHit = SearchHitFixture.builder()
                .id("series-1")
                .title("Series vui")
                .itemType("series")
                .genres(List.of("Hài hước", "Comedy", "Funny"))
                .build();
        SearchHit longEpisode = SearchHitFixture.builder()
                .id("ep-long")
                .title("Tập dài")
                .itemType("episode")
                .runtime("120m")
                .genres(List.of("Hài hước", "Comedy", "Funny"))
                .build();
        SearchHit zeroScore = SearchHitFixture.builder()
                .id("ep-zero")
                .title("Tập thường")
                .itemType("episode")
                .runtime("20m")
                .description("Bình thường")
                .build();
        SearchHit shortLow = SearchHitFixture.builder()
                .id("ep-short-low")
                .title("Tập ngắn 1")
                .itemType("episode")
                .runtime("22m")
                .keywords(List.of("vui"))
                .build();
        SearchHit shortHigh = SearchHitFixture.builder()
                .id("ep-short-high")
                .title("Tập ngắn 2")
                .itemType("episode")
                .runtime("22m")
                .genres(List.of("Hài hước"))
                .keywords(List.of("vui", "ấm áp"))
                .build();

        CatalogSearch search = stubSearch((query, limit, constraints) -> {
            capturedQuery.set(query);
            capturedLimit.set(limit);
            return List.of(seriesHit, longEpisode, zeroScore, shortLow, shortHigh);
        });

        ChatService service = new ChatService(search, intentParser, new TemplateAnswerGenerator(), ratings);
        List<ChatEvent> events = service.stream("3 tập vui nhộn", List.of()).toList();

        assertThat(capturedQuery.get()).isEqualTo("3 tập vui nhộn");
        assertThat(capturedLimit.get()).isEqualTo(100);

        ChatDonePayload done = (ChatDonePayload) events.getLast().data();
        assertThat(done.recommendedMovies()).extracting(SearchHit::id)
                .containsExactly("ep-short-high", "ep-short-low", "ep-long");
        assertThat(done.followUps()).isEmpty();
        assertThat(done.text()).contains("Tập ngắn 2", "Tập ngắn 1", "Tập dài");
    }

    @Test
    void emitsExactPythonErrorMessagesOnSearchFailures() {
        CatalogSearch busySearch = stubSearch((query, limit, constraints) -> {
            throw new ChatCapacityException("busy");
        });
        ChatService busyService = new ChatService(busySearch, intentParser, new TemplateAnswerGenerator(), ratings);
        List<ChatEvent> busyEvents = busyService.stream("tìm Jadoo", List.of()).toList();
        assertThat(busyEvents).hasSize(1);
        assertThat(busyEvents.getFirst().type()).isEqualTo("error");
        assertThat(busyEvents.getFirst().data())
                .isEqualTo(Map.of("text", "CineBot đang hơi quá tải, bạn thử lại sau chút nhé."));

        CatalogSearch brokenSearch = stubSearch((query, limit, constraints) -> {
            throw new IllegalStateException("boom");
        });
        ChatService brokenService = new ChatService(brokenSearch, intentParser, new TemplateAnswerGenerator(), ratings);
        List<ChatEvent> brokenEvents = brokenService.stream("tìm Jadoo", List.of()).toList();
        assertThat(brokenEvents).hasSize(1);
        assertThat(brokenEvents.getFirst().type()).isEqualTo("error");
        assertThat(brokenEvents.getFirst().data())
                .isEqualTo(Map.of("text", "CineBot đang tạm gián đoạn, bạn thử tìm trực tiếp trong Kho phim nhé."));
    }

    @Test
    void reflectsCloudConversationStageInDoneEvent() {
        SearchHit hit = SearchHitFixture.builder()
                .id("ep-1")
                .title("Tập 22")
                .evidence(List.of(new SearchEvidence("sc-1", "Chi làm vỡ bình hoa.", 1000L, null, "scene")))
                .build();
        CatalogSearch search = stubSearch((query, limit, constraints) -> List.of(hit));
        AnswerGenerator cloudStub = (intent, results, onStage) -> {
            onStage.accept("cloud");
            return Stream.of("Có vẻ bạn đang tìm Tập 22. ", "Chi làm vỡ bình hoa.");
        };

        ChatService service = new ChatService(search, intentParser, cloudStub, ratings);
        List<ChatEvent> events = service.stream("Chi làm vỡ bình hoa", List.of()).toList();

        ChatMetadata metadata = (ChatMetadata) events.getFirst().data();
        assertThat(metadata.pipelineStages()).isEqualTo(new ChatPipelineStages("java-lexical", "template", true));

        ChatDonePayload done = (ChatDonePayload) events.getLast().data();
        assertThat(done.pipelineStages()).isEqualTo(new ChatPipelineStages("java-lexical", "cloud", true));
        assertThat(done.text()).isEqualTo("Có vẻ bạn đang tìm Tập 22. Chi làm vỡ bình hoa.");
    }

    private static CatalogSearch stubSearch(SearchFunction handler) {
        return new CatalogSearch() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public List<SearchHit> search(String query, int limit, SearchConstraints constraints) {
                return handler.search(query, limit, constraints);
            }
        };
    }

    // Hàm giả lập truy vấn kho phim cho các ca test trong ChatServiceTest.
    @FunctionalInterface
    private interface SearchFunction {
        List<SearchHit> search(String query, int limit, SearchConstraints constraints);
    }
}
