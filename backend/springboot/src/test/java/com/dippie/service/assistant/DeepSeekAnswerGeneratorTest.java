package com.dippie.service.assistant;

import com.dippie.config.assistant.AssistantProperties;
import com.dippie.model.assistant.ChatIntent;
import com.dippie.model.assistant.SearchEvidence;
import com.dippie.model.assistant.SearchHit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// dựng HttpServer giả để test DeepSeekAnswerGenerator đọc SSE, chặn bịa chi tiết, trần quota ngày với rớt về Template
class DeepSeekAnswerGeneratorTest {

    private static HttpServer server;
    private static ExecutorService executor;
    private static int port;
    private static final AtomicInteger callCount = new AtomicInteger();
    private static final AtomicReference<String> lastAuthHeader = new AtomicReference<>();
    private static final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private static final AtomicReference<HandlerMode> mode = new AtomicReference<>(HandlerMode.VALID_SSE);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntentParser intentParser = new IntentParser();

    // mấy kiểu response giả của upstream DeepSeek cho từng ca test
    enum HandlerMode {
        VALID_SSE,
        STATUS_503,
        WRONG_CONTENT_TYPE,
        TRUNCATED_LENGTH,
        UNGROUNDED_WORDS,
        UNSUPPORTED_DIGIT,
        LEAKED_SECRET,
        MALFORMED_JSON,
        OVER_1200_CHARS,
        STALLED_SSE
    }

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        port = server.getAddress().getPort();

        server.createContext("/chat/completions", exchange -> {
            callCount.incrementAndGet();
            lastAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try (InputStream requestBody = exchange.getRequestBody()) {
                lastRequestBody.set(new String(requestBody.readAllBytes(), StandardCharsets.UTF_8));
            }

            HandlerMode current = mode.get();
            if (current == HandlerMode.STATUS_503) {
                byte[] body = "{\"error\":\"down\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(503, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
                return;
            }
            if (current == HandlerMode.WRONG_CONTENT_TYPE) {
                byte[] body = "{\"choices\":[]}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            if (current == HandlerMode.STALLED_SSE) {
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write("data: {\"choices\":[{\"delta\":{\"content\":\"Chi \"}}]}\n\n".getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    Thread.sleep(3000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } catch (IOException ignored) {
                }
                return;
            }
            try (OutputStream output = exchange.getResponseBody()) {
                String sse = switch (current) {
                    case VALID_SSE -> "data: {\"choices\":[{\"delta\":{\"content\":\"Trong cảnh phim này, \"}}]}\n\n"
                            + "data: {\"choices\":[],\"usage\":{\"total_tokens\":10}}\n\n"
                            + "data: {\"choices\":[{\"delta\":{\"content\":\"Chi làm vỡ bình hoa rất thú vị.\"},\"finish_reason\":\"stop\"}]}\n\n"
                            + "data: [DONE]\n\n";
                    case TRUNCATED_LENGTH -> "data: {\"choices\":[{\"delta\":{\"content\":\"Chi làm vỡ\"},\"finish_reason\":\"length\"}]}\n\n"
                            + "data: [DONE]\n\n";
                    case UNGROUNDED_WORDS -> "data: {\"choices\":[{\"delta\":{\"content\":\"Chi bay lên mặt trăng cùng phi thuyền.\"},\"finish_reason\":\"stop\"}]}\n\n"
                            + "data: [DONE]\n\n";
                    case UNSUPPORTED_DIGIT -> "data: {\"choices\":[{\"delta\":{\"content\":\"Chi làm vỡ 2 bình hoa.\"},\"finish_reason\":\"stop\"}]}\n\n"
                            + "data: [DONE]\n\n";
                    case LEAKED_SECRET -> "data: {\"choices\":[{\"delta\":{\"content\":\"Chi secret-key-123\"},\"finish_reason\":\"stop\"}]}\n\n"
                            + "data: [DONE]\n\n";
                    case MALFORMED_JSON -> "data: {not-valid-json}\n\ndata: [DONE]\n\n";
                    case OVER_1200_CHARS -> "data: {\"choices\":[{\"delta\":{\"content\":\""
                            + "Chi ".repeat(350)
                            + "\"},\"finish_reason\":\"stop\"}]}\n\n"
                            + "data: [DONE]\n\n";
                    default -> "";
                };
                output.write(sse.getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
        });

        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @BeforeEach
    void resetState() {
        callCount.set(0);
        lastAuthHeader.set(null);
        lastRequestBody.set(null);
        mode.set(HandlerMode.VALID_SSE);
    }

    @Test
    void streamsGroundedSummaryFromDeepSeekSseAndSetsCloudStage() throws Exception {
        try (DeepSeekAnswerGenerator generator = new DeepSeekAnswerGenerator(
                new AssistantProperties.Llm("deepseek", "http://127.0.0.1:" + port, "deepseek-chat", "secret-key-123", Duration.ofSeconds(5)),
                objectMapper
        )) {
            ChatIntent intent = intentParser.parse("cảnh Chi làm vỡ bình hoa");
            SearchHit hit = sampleHit();

            AtomicReference<String> stage = new AtomicReference<>();
            List<String> tokens = generator.answer(intent, List.of(hit), stage::set).toList();

            assertThat(callCount.get()).isEqualTo(1);
            assertThat(stage.get()).isEqualTo("cloud");
            assertThat(tokens).containsExactly(
                    "Có vẻ bạn đang tìm \"Tập 22\", đoạn quanh phút 08:40. ",
                    "Trong cảnh phim này, Chi làm vỡ bình hoa rất thú vị."
            );
            assertThat(lastAuthHeader.get()).isEqualTo("Bearer secret-key-123");
            assertThat(lastRequestBody.get()).doesNotContain("secret-key-123");

            JsonNode sent = objectMapper.readTree(lastRequestBody.get());
            assertThat(sent.get("model").asText()).isEqualTo("deepseek-chat");
            assertThat(sent.get("stream").asBoolean()).isTrue();
            assertThat(sent.get("messages").get(0).get("role").asText()).isEqualTo("system");
            assertThat(sent.get("messages").get(0).get("content").asText()).contains("Bạn là CineBot");
            JsonNode userContext = objectMapper.readTree(sent.get("messages").get(1).get("content").asText());
            assertThat(userContext.get("intent").get("scope").asText()).isEqualTo("scene");
            assertThat(userContext.get("sources").get(0).get("evidence").get(0).asText())
                    .isEqualTo("Chi làm vỡ bình hoa.");
        }
    }

    @Test
    void fallsBackToUnavailableMessageWhenProviderReturns503OrMissingApiKey() {
        mode.set(HandlerMode.STATUS_503);
        try (DeepSeekAnswerGenerator generator = new DeepSeekAnswerGenerator(
                new AssistantProperties.Llm("deepseek", "http://127.0.0.1:" + port, "deepseek-chat", "secret-key-123", Duration.ofSeconds(5)),
                objectMapper
        )) {
            ChatIntent intent = intentParser.parse("cảnh Chi làm vỡ bình hoa");

            AtomicReference<String> stage = new AtomicReference<>();
            String answer = String.join("", generator.answer(intent, List.of(sampleHit()), stage::set).toList());

            assertThat(stage.get()).isEqualTo("template");
            assertThat(answer).isEqualTo(
                    "Có vẻ bạn đang tìm \"Tập 22\", đoạn quanh phút 08:40. Phần trả lời AI đang tạm bận, gửi bạn đoạn trích gốc nhé. Chi làm vỡ bình hoa."
            );

            try (DeepSeekAnswerGenerator noKey = new DeepSeekAnswerGenerator(
                    new AssistantProperties.Llm("deepseek", "http://127.0.0.1:" + port, "deepseek-chat", "", Duration.ofSeconds(5)),
                    objectMapper
            )) {
                AtomicReference<String> noKeyStage = new AtomicReference<>();
                String noKeyAnswer = String.join("", noKey.answer(intent, List.of(sampleHit()), noKeyStage::set).toList());
                assertThat(noKeyStage.get()).isEqualTo("template");
                assertThat(noKeyAnswer).contains("Phần trả lời AI đang tạm bận, gửi bạn đoạn trích gốc nhé. Chi làm vỡ bình hoa.");
                assertThat(callCount.get()).isEqualTo(1);
            }
        }
    }

    @Test
    void fallsBackToPlainEvidenceOnProtocolAndGroundingViolations() {
        try (DeepSeekAnswerGenerator generator = new DeepSeekAnswerGenerator(
                new AssistantProperties.Llm("deepseek", "http://127.0.0.1:" + port, "deepseek-chat", "secret-key-123", Duration.ofSeconds(5)),
                objectMapper
        )) {
            ChatIntent intent = intentParser.parse("cảnh Chi làm vỡ bình hoa");
            String expectedFallback = "Có vẻ bạn đang tìm \"Tập 22\", đoạn quanh phút 08:40. Chi làm vỡ bình hoa.";

            List<HandlerMode> protocolModes = List.of(
                    HandlerMode.WRONG_CONTENT_TYPE,
                    HandlerMode.TRUNCATED_LENGTH,
                    HandlerMode.UNGROUNDED_WORDS,
                    HandlerMode.UNSUPPORTED_DIGIT,
                    HandlerMode.LEAKED_SECRET,
                    HandlerMode.MALFORMED_JSON,
                    HandlerMode.OVER_1200_CHARS
            );

            for (HandlerMode protocolMode : protocolModes) {
                mode.set(protocolMode);
                AtomicReference<String> stage = new AtomicReference<>();
                String text = String.join("", generator.answer(intent, List.of(sampleHit()), stage::set).toList());
                assertThat(stage.get()).as("stage for %s", protocolMode).isEqualTo("template");
                assertThat(text).as("fallback for %s", protocolMode).isEqualTo(expectedFallback);
            }
        }
    }

    @Test
    void delegatesChitchatMoodSeriesAndNoEvidenceToTemplateWithoutHttpCall() {
        try (DeepSeekAnswerGenerator generator = new DeepSeekAnswerGenerator(
                new AssistantProperties.Llm("deepseek", "http://127.0.0.1:" + port, "deepseek-chat", "secret-key-123", Duration.ofSeconds(5)),
                objectMapper
        )) {

            ChatIntent chitchat = intentParser.parse("Xin chào!");
            assertThat(String.join("", generator.answer(chitchat, List.of()).toList()))
                    .isEqualTo("Mình là CineBot. Bạn cứ kể lại cảnh phim, nhân vật hoặc tập muốn tìm nhé.");

            ChatIntent mood = intentParser.parse("hôm nay stress quá");
            assertThat(String.join("", generator.answer(mood, List.of(sampleHit())).toList()))
                    .isEqualTo("Bạn có thể xem tập \"Tập 22\".");

            ChatIntent noEvidence = intentParser.parse("tìm tập 22");
            SearchHit emptyEvidenceHit = SearchHitFixture.builder().title("Tập 22").build();
            assertThat(String.join("", generator.answer(noEvidence, List.of(emptyEvidenceHit)).toList()))
                    .isEqualTo("Có vẻ bạn đang tìm \"Tập 22\".");

            assertThat(callCount.get()).isZero();
        }
    }

    @Test
    void enforcesDailyCallBudgetAndFallsBackOnStalledSseBodyReadTimeout() {
        // đặt quota ngày bằng 1 thì phát đầu gọi DeepSeek, phát hai rớt về bản mẫu luôn chứ đừng bắn thêm HTTP
        try (DeepSeekAnswerGenerator budgeted = new DeepSeekAnswerGenerator(
            new AssistantProperties.Llm(
                    "deepseek", "http://127.0.0.1:" + port, "deepseek-chat", "secret-key-123",
                    Duration.ofSeconds(5), Duration.ofSeconds(2), 2, 1
            ),
            objectMapper
        )) {
            ChatIntent intent = intentParser.parse("cảnh Chi làm vỡ bình hoa");
            AtomicReference<String> firstStage = new AtomicReference<>();
            budgeted.answer(intent, List.of(sampleHit()), firstStage::set).toList();
            assertThat(firstStage.get()).isEqualTo("cloud");
            assertThat(callCount.get()).isEqualTo(1);

            AtomicReference<String> secondStage = new AtomicReference<>();
            String fallbackText = String.join("", budgeted.answer(intent, List.of(sampleHit()), secondStage::set).toList());
            assertThat(secondStage.get()).isEqualTo("template");
            assertThat(fallbackText).isEqualTo("Có vẻ bạn đang tìm \"Tập 22\", đoạn quanh phút 08:40. Chi làm vỡ bình hoa.");
            assertThat(callCount.get()).isEqualTo(1);
        }

        // cho upstream trả 200 OK rồi đứng hình 3 giây để check watchdog cắt stream trước 2 giây
        mode.set(HandlerMode.STALLED_SSE);
        try (DeepSeekAnswerGenerator timed = new DeepSeekAnswerGenerator(
            new AssistantProperties.Llm(
                    "deepseek", "http://127.0.0.1:" + port, "deepseek-chat", "secret-key-123",
                    Duration.ofMillis(350), Duration.ofSeconds(2), 2, 10
            ),
            objectMapper
        )) {
            ChatIntent intent = intentParser.parse("cảnh Chi làm vỡ bình hoa");
            AtomicReference<String> stage = new AtomicReference<>();
            long startNanos = System.nanoTime();
            String answer = String.join("", timed.answer(intent, List.of(sampleHit()), stage::set).toList());
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            assertThat(stage.get()).isEqualTo("template");
            assertThat(answer).contains("Phần trả lời AI đang tạm bận, gửi bạn đoạn trích gốc nhé.");
            assertThat(elapsedMs).isLessThan(2000L);
        }
    }

    private static SearchHit sampleHit() {
        return SearchHitFixture.builder()
                .id("ep_jadoo_s01_0022")
                .episodeId("ep_jadoo_s01_0022")
                .title("Tập 22")
                .startMs(520000L)
                .evidence(List.of(new SearchEvidence("scene-22", "Chi làm vỡ bình hoa.", 520000L, null, "scene")))
                .build();
    }
}
