package com.dippie.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ChatProxyControllerTest {

    private static HttpServer server;
    private static int port;
    private static final AtomicInteger chatCalls = new AtomicInteger();
    private static final AtomicReference<String> lastChatRequestBody = new AtomicReference<>();
    private static volatile CountDownLatch releaseSecondChunk;

    private final MockMvc mockMvc;
    private final ChatProxyController chatProxyController;

    ChatProxyControllerTest(MockMvc mockMvc, ChatProxyController chatProxyController) {
        this.mockMvc = mockMvc;
        this.chatProxyController = chatProxyController;
    }

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        port = server.getAddress().getPort();

        server.createContext("/api/chat", exchange -> {
            chatCalls.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastChatRequestBody.set(body);

            if (body.contains("\"trigger400\"")) {
                writeJson(exchange, 400, "{\"detail\":\"Thiếu trường query hoặc messages.\"}");
                return;
            }
            if (body.contains("\"trigger503\"")) {
                writeJson(exchange, 503, "{\"detail\":\"Chưa có snapshot catalog được chấp nhận.\"}");
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write("event: status\ndata: {\"step\":\"retrieving\"}\n\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                CountDownLatch latch = releaseSecondChunk;
                if (latch != null) {
                    try {
                        latch.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                output.write("event: token\ndata: {\"text\":\"Conan\"}\n\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                output.write("event: done\ndata: {\"answer\":\"Thám tử lừng danh\"}\n\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
        });

        server.createContext("/v1/discover", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("\"query\":\"\"")) {
                writeJson(exchange, 400, "{\"detail\":\"Thiếu trường query.\"}");
                return;
            }
            writeJson(exchange, 200, "{\"results\":[{\"id\":\"discover-1\"}],\"explanation\":\"OK\"}");
        });

        server.createContext("/v1/search", exchange -> {
            exchange.getRequestBody().readAllBytes();
            writeJson(exchange, 200, "{\"results\":[{\"id\":\"search-1\"}]}");
        });

        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("dippie.ai.base-url", () -> "http://127.0.0.1:" + port);
        registry.add("dippie.ai.connect-timeout", () -> "PT1S");
        registry.add("dippie.ai.request-timeout", () -> "PT5S");
    }

    @BeforeEach
    void resetState() {
        chatCalls.set(0);
        lastChatRequestBody.set(null);
        releaseSecondChunk = null;
    }

    @Test
    void streamsSseEventsInOrderWithRequiredHeaders() throws Exception {
        String payload = "{\"query\":\"gợi ý phim trinh thám\"}";

        MvcResult started = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completed = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/event-stream; charset=utf-8"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform"))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andReturn();

        String content = completed.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(lastChatRequestBody.get()).isEqualTo(payload);

        int firstIndex = content.indexOf("event: status");
        int secondIndex = content.indexOf("event: token");
        int thirdIndex = content.indexOf("event: done");
        assertThat(firstIndex).isGreaterThanOrEqualTo(0);
        assertThat(secondIndex).isGreaterThan(firstIndex);
        assertThat(thirdIndex).isGreaterThan(secondIndex);
        assertThat(content).contains("Thám tử lừng danh");
    }

    @Test
    void flushesFirstChunkBeforeUpstreamWritesNextChunk() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        releaseSecondChunk = gate;

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/chat");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("{\"query\":\"stream-check\"}".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<StreamingResponseBody> response = chatProxyController.chat(request);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();

        StreamingResponseBody body = response.getBody();
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        AtomicInteger flushCount = new AtomicInteger();

        OutputStream trackingStream = new OutputStream() {
            @Override
            public void write(int b) {
                collected.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                collected.write(b, off, len);
            }

            @Override
            public void flush() {
                flushCount.incrementAndGet();
                if (collected.toString(StandardCharsets.UTF_8).contains("event: status")) {
                    gate.countDown();
                }
            }
        };

        body.writeTo(trackingStream);

        assertThat(gate.getCount()).isZero();
        assertThat(flushCount.get()).isGreaterThanOrEqualTo(2);
        assertThat(collected.toString(StandardCharsets.UTF_8)).contains("event: done");
    }

    @Test
    void passesThroughUpstreamNon200StatusAndJsonBodyUnchanged() throws Exception {
        MvcResult badRequest = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"trigger400\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(badRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Thiếu trường query hoặc messages."));

        MvcResult unavailable = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"trigger503\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(unavailable))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Chưa có snapshot catalog được chấp nhận."));
    }

    @Test
    void rejectsPayloadLargerThan64KbWith413EnvelopeAndAcceptsExact64Kb() throws Exception {
        byte[] oversized = new byte[ChatProxyController.MAX_REQUEST_BYTES + 1];
        java.util.Arrays.fill(oversized, (byte) 'a');

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversized))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Yêu cầu quá lớn."))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(chatCalls.get()).isZero();

        String prefix = "{\"query\":\"";
        String suffix = "\"}";
        int paddingLength = ChatProxyController.MAX_REQUEST_BYTES - prefix.length() - suffix.length();
        String exactBoundary = prefix + "x".repeat(paddingLength) + suffix;
        assertThat(exactBoundary.getBytes(StandardCharsets.UTF_8)).hasSize(ChatProxyController.MAX_REQUEST_BYTES);

        MvcResult started = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exactBoundary))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk());
        assertThat(chatCalls.get()).isEqualTo(1);
    }

    @Test
    void passesThroughDiscoverAndSearchEndpoints() throws Exception {
        mockMvc.perform(post("/v1/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"conan\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].id").value("discover-1"));

        mockMvc.perform(post("/v1/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Thiếu trường query."));

        mockMvc.perform(post("/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"mood\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].id").value("search-1"));
    }

    private static void writeJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
