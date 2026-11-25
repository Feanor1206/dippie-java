package com.dippie.assistant.chat;

import com.dippie.assistant.AssistantProperties;
import static org.assertj.core.api.Assertions.assertThat;

import com.dippie.assistant.search.SearchEvidence;
import com.dippie.assistant.search.SearchHit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    enum HandlerMode {
        VALID_SSE,
        STATUS_503,
        WRONG_CONTENT_TYPE,
        TRUNCATED_LENGTH,
        UNGROUNDED_WORDS,
        UNSUPPORTED_DIGIT,
        LEAKED_SECRET,
        MALFORMED_JSON,
        OVER_1200_CHARS
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
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

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
        DeepSeekAnswerGenerator generator = new DeepSeekAnswerGenerator(
                new AssistantProperties.Llm("deepseek", "http://127.0.0.1:" + port, "deepseek-chat", "secret-key-123", Duration.ofSeconds(5)),
                objectMapper
        );
        ChatIntent intent = intentParser.parse("cảnh Chi làm vỡ bình hoa");
        SearchHit hit = sampleHit();

        AtomicReference<String> stage = new AtomicReference<>();
        List<String> tokens = generator.answer(intent, List.of(hit), stage::set).toList();

        assertThat(callCount.get()).isEqualTo(1);
        assertThat(stage.get()).isEqualTo("cloud");
        assertThat(tokens).containsExactly(
                "Có vẻ bạn đang tìm Tập 22, khoảng 08:40. ",
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

    @Test
    void fallsBackToUnavailableMessageWhenProviderReturns503OrMissingApiKey() {
        mode.set(HandlerMode.STATUS_503);
        DeepSeekAnswerGenerator generator = new DeepSeekAnswerGenerator(
                new AssistantProperties.Llm("deepseek", "http://127.0.0.1:" + port, "deepseek-chat", "secret-key-123", Duration.ofSeconds(5)),
                objectMapper
        );
        ChatIntent intent = intentParser.parse("cảnh Chi làm vỡ bình hoa");

        AtomicReference<String> stage = new AtomicReference<>();
        String answer = String.join("", generator.answer(intent, List.of(sampleHit()), stage::set).toList());

        assertThat(stage.get()).isEqualTo("template");
        assertThat(answer).isEqualTo(
                "Có vẻ bạn đang tìm Tập 22, khoảng 08:40. Dịch vụ hội thoại hiện chưa sẵn sàng. Chi làm vỡ bình hoa."
        );

        DeepSeekAnswerGenerator noKey = new DeepSeekAnswerGenerator(
                new AssistantProperties.Llm("deepseek", "http://127.0.0.1:" + port, "deepseek-chat", "", Duration.ofSeconds(5)),
                objectMapper
        );
        AtomicReference<String> noKeyStage = new AtomicReference<>();
        String noKeyAnswer = String.join("", noKey.answer(intent, List.of(sampleHit()), noKeyStage::set).toList());
        assertThat(noKeyStage.get()).isEqualTo("template");
        assertThat(noKeyAnswer).contains("Dịch vụ hội thoại hiện chưa sẵn sàng. Chi làm vỡ bình hoa.");
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void fallsBackToPlainEvidenceOnProtocolAndGroundingViolations() {
        DeepSeekAnswerGenerator generator = new DeepSeekAnswerGenerator(
                new AssistantProperties.Llm("deepseek", "http://127.0.0.1:" + port, "deepseek-chat", "secret-key-123", Duration.ofSeconds(5)),
                objectMapper
        );
        ChatIntent intent = intentParser.parse("cảnh Chi làm vỡ bình hoa");
        String expectedFallback = "Có vẻ bạn đang tìm Tập 22, khoảng 08:40. Chi làm vỡ bình hoa.";

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

    @Test
    void delegatesChitchatMoodSeriesAndNoEvidenceToTemplateWithoutHttpCall() {
        DeepSeekAnswerGenerator generator = new DeepSeekAnswerGenerator(
                new AssistantProperties.Llm("deepseek", "http://127.0.0.1:" + port, "deepseek-chat", "secret-key-123", Duration.ofSeconds(5)),
                objectMapper
        );

        ChatIntent chitchat = intentParser.parse("Xin chào!");
        assertThat(String.join("", generator.answer(chitchat, List.of()).toList()))
                .isEqualTo("Mình là CineBot. Bạn có thể mô tả cảnh hoặc tập phim muốn tìm trong danh mục.");

        ChatIntent mood = intentParser.parse("hôm nay stress quá");
        assertThat(String.join("", generator.answer(mood, List.of(sampleHit())).toList()))
                .isEqualTo("Bạn thử tập này nhé.");

        ChatIntent noEvidence = intentParser.parse("tìm tập 22");
        SearchHit emptyEvidenceHit = SearchHitFixture.builder().title("Tập 22").build();
        assertThat(String.join("", generator.answer(noEvidence, List.of(emptyEvidenceHit)).toList()))
                .isEqualTo("Có vẻ bạn đang tìm Tập 22.");

        assertThat(callCount.get()).isZero();
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
