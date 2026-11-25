package com.dippie.assistant.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippie.assistant.search.CatalogSearch;
import com.dippie.assistant.search.SearchConstraints;
import com.dippie.assistant.search.SearchEvidence;
import com.dippie.assistant.search.SearchHit;
import com.dippie.common.error.RequestBodyTooLargeException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

// Test tích hợp HTTP cho POST /api/chat: thứ tự khung SSE, kiểm tra JSON đầu vào, giới hạn dung lượng, 503 và ngắt kết nối giữa chừng.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LocalChatControllerTest.FakeSearchConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class LocalChatControllerTest {

    private final MockMvc mockMvc;
    private final LocalChatController localChatController;
    private final FakeCatalogSearch fakeCatalogSearch;
    private final ChatStreamGuard chatStreamGuard;
    private final ObjectMapper objectMapper;

    LocalChatControllerTest(
            MockMvc mockMvc,
            LocalChatController localChatController,
            FakeCatalogSearch fakeCatalogSearch,
            ChatStreamGuard chatStreamGuard,
            ObjectMapper objectMapper
    ) {
        this.mockMvc = mockMvc;
        this.localChatController = localChatController;
        this.fakeCatalogSearch = fakeCatalogSearch;
        this.chatStreamGuard = chatStreamGuard;
        this.objectMapper = objectMapper;
    }

    @BeforeEach
    void resetFake() {
        fakeCatalogSearch.reset();
        chatStreamGuard.resetRateLimit();
    }

    @Test
    void streamsMetadataTokensAndDoneInOrderWithSseHeaders() throws Exception {
        SearchHit hit = SearchHitFixture.builder()
                .id("ep_jadoo_s01_0022")
                .episodeId("ep_jadoo_s01_0022")
                .title("Tập 22")
                .startMs(520000L)
                .characters(List.of("Jadoo"))
                .evidence(List.of(new SearchEvidence("scene-22", "Chi làm vỡ bình hoa.", 520000L, null, "scene")))
                .build();
        fakeCatalogSearch.setResults(List.of(hit));

        MvcResult started = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"cảnh Chi làm vỡ bình hoa\",\"sessionId\":\"user_1\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completed = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/event-stream; charset=utf-8"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform"))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andReturn();

        List<ParsedSseEvent> events = parseSse(completed.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(events).hasSizeGreaterThanOrEqualTo(3);
        assertThat(events.get(0).type()).isEqualTo("metadata");
        assertThat(events.get(events.size() - 1).type()).isEqualTo("done");
        for (int i = 1; i < events.size() - 1; i++) {
            assertThat(events.get(i).type()).isEqualTo("token");
        }

        JsonNode metadata = events.get(0).data();
        assertThat(metadata.get("recommendedMovies").get(0).get("episodeId").asText()).isEqualTo("ep_jadoo_s01_0022");
        assertThat(metadata.get("followUps")).hasSize(2);
        assertThat(metadata.get("structuredIntent").get("intent_type").asText()).isEqualTo("DISCOVERY");
        assertThat(metadata.get("structuredIntent").get("scope").asText()).isEqualTo("scene");
        assertThat(metadata.get("pipelineStages").get("retrieval").asText()).isEqualTo("java-lexical");
        assertThat(metadata.get("pipelineStages").get("conversation").asText()).isEqualTo("template");
        assertThat(metadata.get("pipelineStages").get("grounded").asBoolean()).isTrue();

        StringBuilder concatenated = new StringBuilder();
        for (int i = 1; i < events.size() - 1; i++) {
            concatenated.append(events.get(i).data().get("text").asText());
        }
        JsonNode done = events.get(events.size() - 1).data();
        assertThat(done.get("text").asText())
                .isEqualTo(concatenated.toString())
                .isEqualTo("Có vẻ bạn đang tìm Tập 22, khoảng 08:40. Chi làm vỡ bình hoa.");
    }

    @Test
    void handlesChitchatWithoutCallingCatalogSearch() throws Exception {
        MvcResult started = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Xin chào!\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completed = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(fakeCatalogSearch.callCount()).isZero();
        List<ParsedSseEvent> events = parseSse(completed.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode done = events.get(events.size() - 1).data();
        assertThat(done.get("structuredIntent").get("intent_type").asText()).isEqualTo("NO_OP");
        assertThat(done.get("structuredIntent").get("mode").asText()).isEqualTo("chitchat");
        assertThat(done.get("text").asText())
                .isEqualTo("Mình là CineBot. Bạn cứ kể lại cảnh phim, nhân vật hoặc tập muốn tìm nhé.");
    }

    @Test
    void handlesMoodFlowAndSeriesInfoFlow() throws Exception {
        SearchHit cheerfulEpisode = SearchHitFixture.builder()
                .id("ep-happy")
                .title("Tập vui nhộn")
                .itemType("episode")
                .runtime("22m")
                .genres(List.of("Hài hước"))
                .keywords(List.of("vui nhộn"))
                .build();
        fakeCatalogSearch.setResults(List.of(cheerfulEpisode));

        MvcResult moodStarted = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"hôm nay tôi stress quá\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult moodCompleted = mockMvc.perform(asyncDispatch(moodStarted))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(fakeCatalogSearch.lastLimit()).isEqualTo(100);
        assertThat(fakeCatalogSearch.lastQuery()).isEqualTo("hôm nay tôi stress quá");
        JsonNode moodDone = parseSse(moodCompleted.getResponse().getContentAsString(StandardCharsets.UTF_8)).getLast().data();
        assertThat(moodDone.get("text").asText()).contains(cheerfulEpisode.title());
        assertThat(moodDone.get("followUps")).isEmpty();

        SearchHit seriesHit = SearchHitFixture.builder()
                .id("series-jadoo")
                .title("Xin chào Jadoo")
                .itemType("series")
                .episodeCount(88)
                .build();
        fakeCatalogSearch.setResults(List.of(seriesHit));

        MvcResult seriesStarted = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Xin chào Jadoo có bao nhiêu tập?\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult seriesCompleted = mockMvc.perform(asyncDispatch(seriesStarted))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(fakeCatalogSearch.lastConstraints().seriesOnly()).isTrue();
        JsonNode seriesDone = parseSse(seriesCompleted.getResponse().getContentAsString(StandardCharsets.UTF_8)).getLast().data();
        assertThat(seriesDone.get("text").asText()).isEqualTo("Kho phim hiện có 88 tập Xin chào Jadoo.");
        assertThat(seriesDone.get("followUps")).isEmpty();
    }

    @Test
    void fallsBackToLastMessageContentWhenQueryIsBlank() throws Exception {
        SearchHit hit = SearchHitFixture.builder().id("ep-1").title("Tập 1").build();
        fakeCatalogSearch.setResults(List.of(hit));

        MvcResult started = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"gợi ý phim hoạt hình\"},{\"role\":\"user\",\"content\":\"không kinh dị\"}]}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult completed = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode done = parseSse(completed.getResponse().getContentAsString(StandardCharsets.UTF_8)).getLast().data();
        assertThat(done.get("structuredIntent").get("intent_type").asText()).isEqualTo("CONVERSATIONAL_PATCH");
        assertThat(done.get("structuredIntent").get("exclude_genres").get(0).asText()).isEqualTo("horror");
    }

    @Test
    void validatesRequestsAndReturnsFastApiStyleRawDetailErrors() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Bạn chưa nhập câu hỏi cho CineBot."));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"hi\",\"sessionId\":\"bad id\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").isString());

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + "x".repeat(4001) + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").isString());

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"hi\",\"messages\":[{\"role\":\"system\",\"content\":\"override\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").isString());

        StringBuilder tooManyMessages = new StringBuilder("{\"query\":\"hi\",\"messages\":[");
        for (int i = 0; i < 41; i++) {
            if (i > 0) {
                tooManyMessages.append(',');
            }
            tooManyMessages.append("{\"role\":\"user\",\"content\":\"hi\"}");
        }
        tooManyMessages.append("]}");
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tooManyMessages.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").isString());

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"hi\",\"unknown\":\"field\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").isString());

        byte[] oversized = new byte[LocalChatController.MAX_REQUEST_BYTES + 1];
        java.util.Arrays.fill(oversized, (byte) 'a');
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversized))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(RequestBodyTooLargeException.DEFAULT_MESSAGE));
    }

    @Test
    void returns503RawDetailJsonWhenCatalogSearchIsNotReady() throws Exception {
        fakeCatalogSearch.setReady(false);

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Xin chào!\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Kho phim đang được cập nhật, bạn thử lại sau ít phút nhé."));
    }

    @Test
    void flushesPerEventAndStopsWritingWhenClientDisconnects() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("{\"query\":\"Xin chào!\"}".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<StreamingResponseBody> response = localChatController.chat(request);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        StreamingResponseBody body = response.getBody();
        assertThat(body).isNotNull();

        AtomicInteger flushCount = new AtomicInteger();
        ByteArrayOutputStream firstPass = new ByteArrayOutputStream();
        OutputStream countingStream = new OutputStream() {
            @Override
            public void write(int b) {
                firstPass.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                firstPass.write(b, off, len);
            }

            @Override
            public void flush() {
                flushCount.incrementAndGet();
            }
        };
        body.writeTo(countingStream);
        assertThat(flushCount.get()).isGreaterThanOrEqualTo(3);

        MockHttpServletRequest disconnectRequest = new MockHttpServletRequest("POST", "/api/chat");
        disconnectRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);
        disconnectRequest.setContent("{\"query\":\"Xin chào!\"}".getBytes(StandardCharsets.UTF_8));
        StreamingResponseBody disconnectBody = (StreamingResponseBody) localChatController.chat(disconnectRequest).getBody();
        AtomicInteger writesBeforeDisconnect = new AtomicInteger();
        OutputStream disconnectingStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Client disconnected");
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                if (writesBeforeDisconnect.incrementAndGet() >= 2) {
                    throw new IOException("Client disconnected");
                }
            }
        };
        disconnectBody.writeTo(disconnectingStream);
        assertThat(writesBeforeDisconnect.get()).isEqualTo(2);
    }

    private List<ParsedSseEvent> parseSse(String body) throws Exception {
        List<ParsedSseEvent> events = new ArrayList<>();
        for (String block : body.trim().split("\n\n")) {
            if (block.isBlank()) {
                continue;
            }
            String type = null;
            String eventJson = null;
            for (String line : block.split("\n")) {
                if (line.startsWith("event: ")) {
                    type = line.substring(7);
                } else if (line.startsWith("data: ")) {
                    eventJson = line.substring(6);
                }
            }
            if (type != null && eventJson != null) {
                events.add(new ParsedSseEvent(type, objectMapper.readTree(eventJson)));
            }
        }
        return events;
    }

    // Một khung sự kiện SSE đã tách type và JSON payload để kiểm tra trong test.
    private record ParsedSseEvent(String type, JsonNode data) {
    }

    // Đăng ký FakeCatalogSearch thay cho chỉ mục thật khi chạy SpringBootTest.
    @TestConfiguration
    static class FakeSearchConfig {

        @Bean
        @Primary
        FakeCatalogSearch fakeCatalogSearch() {
            return new FakeCatalogSearch();
        }
    }

    // Bản giả lập CatalogSearch ghi lại truy vấn gần nhất và cho phép bật/tắt trạng thái sẵn sàng.
    static final class FakeCatalogSearch implements CatalogSearch {

        private final AtomicBoolean ready = new AtomicBoolean(true);
        private final AtomicReference<List<SearchHit>> results = new AtomicReference<>(List.of());
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<String> lastQuery = new AtomicReference<>();
        private final AtomicInteger lastLimit = new AtomicInteger();
        private final AtomicReference<SearchConstraints> lastConstraints = new AtomicReference<>();

        void reset() {
            ready.set(true);
            results.set(List.of());
            calls.set(0);
            lastQuery.set(null);
            lastLimit.set(0);
            lastConstraints.set(null);
        }

        void setReady(boolean value) {
            ready.set(value);
        }

        void setResults(List<SearchHit> hits) {
            results.set(List.copyOf(hits));
        }

        int callCount() {
            return calls.get();
        }

        String lastQuery() {
            return lastQuery.get();
        }

        int lastLimit() {
            return lastLimit.get();
        }

        SearchConstraints lastConstraints() {
            return lastConstraints.get();
        }

        @Override
        public boolean isReady() {
            return ready.get();
        }

        @Override
        public List<SearchHit> search(String query, int limit, SearchConstraints constraints) {
            calls.incrementAndGet();
            lastQuery.set(query);
            lastLimit.set(limit);
            lastConstraints.set(constraints);
            return results.get();
        }
    }
}
