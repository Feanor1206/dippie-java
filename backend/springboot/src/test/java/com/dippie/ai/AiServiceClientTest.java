package com.dippie.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dippie.catalog.importing.CatalogDocument;
import com.dippie.catalog.importing.CatalogImportService;
import com.dippie.common.error.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;

@SpringBootTest
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AiServiceClientTest {

    private static final String SYNC_TOKEN = "secret-sync-token-123";

    private static HttpServer server;
    private static int port;
    private static final AtomicBoolean readyFlag = new AtomicBoolean(true);
    private static final AtomicReference<String> capturedSyncToken = new AtomicReference<>();
    private static final AtomicReference<String> capturedAccept = new AtomicReference<>();
    private static final AtomicReference<String> firstCatalogSync = new AtomicReference<>();

    private final AiServiceClient aiServiceClient;
    private final ObjectMapper objectMapper;
    private final CatalogImportService imports;

    AiServiceClientTest(AiServiceClient aiServiceClient, ObjectMapper objectMapper, CatalogImportService imports) {
        this.aiServiceClient = aiServiceClient;
        this.objectMapper = objectMapper;
        this.imports = imports;
    }

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        port = server.getAddress().getPort();

        server.createContext("/api/chat", exchange -> {
            capturedAccept.set(exchange.getRequestHeaders().getFirst("Accept"));
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write("event: status\ndata: {\"step\":\"search\"}\n\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                output.write("event: done\ndata: {\"answer\":\"Xin chào\"}\n\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
        });

        server.createContext("/v1/discover", exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (requestBody.contains("\"bad\"")) {
                writeJson(exchange, 400, "{\"detail\":\"Thiếu trường query.\"}");
                return;
            }
            writeJson(exchange, 200, "{\"results\":[{\"id\":\"m1\"}],\"explanation\":\"OK\"}");
        });

        server.createContext("/v1/search", exchange -> {
            exchange.getRequestBody().readAllBytes();
            writeJson(exchange, 200, "{\"results\":[{\"id\":\"m2\"}]}");
        });

        server.createContext("/api/index/sync", exchange -> {
            capturedSyncToken.set(exchange.getRequestHeaders().getFirst("X-Service-Token"));
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode sync = new ObjectMapper().readTree(request);
            if (sync.has("catalog")) {
                long generation = sync.path("generation").asLong();
                firstCatalogSync.compareAndSet(null, request);
                writeJson(exchange, 200, "{\"index_ready\":true,\"generation\":" + generation + "}");
            } else {
                writeJson(exchange, 200, "{\"synced\":true,\"generation\":7}");
            }
        });

        server.createContext("/ready", exchange -> {
            exchange.getRequestBody().readAllBytes();
            if (readyFlag.get()) {
                writeJson(exchange, 200, "{\"ready\":true}");
            } else {
                writeJson(exchange, 503, "{\"ready\":false}");
            }
        });

        server.createContext("/slow/v1/discover", exchange -> {
            exchange.getRequestBody().readAllBytes();
            try {
                Thread.sleep(600);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            writeJson(exchange, 200, "{\"results\":[]}");
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
        registry.add("dippie.ai.service-token", () -> SYNC_TOKEN);
        registry.add("dippie.ai.connect-timeout", () -> "PT1S");
        registry.add("dippie.ai.request-timeout", () -> "PT5S");
    }

    @BeforeEach
    void resetState() {
        readyFlag.set(true);
        capturedSyncToken.set(null);
        capturedAccept.set(null);
    }

    @Test
    void opensChatStreamAndReadsEventsInOrder() throws Exception {
        byte[] payload = "{\"query\":\"conan\"}".getBytes(StandardCharsets.UTF_8);
        try (InputStreamResponse response = aiServiceClient.openChatStream(payload)) {
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.contentType()).contains("text/event-stream");
            assertThat(capturedAccept.get()).isEqualTo("text/event-stream");
            String streamed = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(streamed).isEqualTo(
                    "event: status\ndata: {\"step\":\"search\"}\n\n"
                            + "event: done\ndata: {\"answer\":\"Xin chào\"}\n\n"
            );
        }
    }

    @Test
    void callsDiscoverSearchSyncAndReadyEndpoints() throws Exception {
        JsonNode discoverResult = aiServiceClient.discover(objectMapper.readTree("{\"query\":\"conan\"}"));
        assertThat(discoverResult.path("results").get(0).path("id").asText()).isEqualTo("m1");

        JsonNode searchResult = aiServiceClient.search(objectMapper.readTree("{\"query\":\"happy\"}"));
        assertThat(searchResult.path("results").get(0).path("id").asText()).isEqualTo("m2");

        JsonNode syncResult = aiServiceClient.syncIndex(objectMapper.readTree("{\"revision\":1}"));
        assertThat(syncResult.path("synced").asBoolean()).isTrue();
        assertThat(syncResult.path("generation").asInt()).isEqualTo(7);
        assertThat(capturedSyncToken.get()).isEqualTo(SYNC_TOKEN);

        assertThat(aiServiceClient.isReady()).isTrue();
        readyFlag.set(false);
        assertThat(aiServiceClient.isReady()).isFalse();
    }

    @Test
    void firstImportPublishesGenerationOneToTheAiIndex() throws Exception {
        assertThat(imports.publishCurrentIndex()).isEmpty();

        var result = imports.importPackage(new CatalogDocument(objectMapper.readTree("""
                {"package":{"id":"startup-test"},"series":[{"id":"startup-series"}]}
                """)));
        assertThat(result.snapshotVersion()).isEqualTo(1);
        assertThat(result.indexStatus()).isEqualTo("READY");

        JsonNode request = objectMapper.readTree(firstCatalogSync.get());
        assertThat(request.path("generation").asInt()).isEqualTo(1);
        assertThat(request.path("catalog").path("episodes").isArray()).isTrue();
        assertThat(request.path("force_rebuild").asBoolean()).isFalse();
        assertThat(request.path("content_hash").isNull()).isTrue();
    }

    @Test
    void throwsUpstreamExceptionOnNon2xxDiscoverResponse() throws Exception {
        JsonNode badRequest = objectMapper.readTree("{\"query\":\"bad\"}");
        assertThatThrownBy(() -> aiServiceClient.discover(badRequest))
                .isInstanceOf(AiUpstreamException.class)
                .satisfies(error -> {
                    AiUpstreamException upstream = (AiUpstreamException) error;
                    assertThat(upstream.statusCode()).isEqualTo(400);
                    assertThat(new String(upstream.responseBody(), StandardCharsets.UTF_8))
                            .contains("Thiếu trường query.");
                });
    }

    @Test
    void throwsAiUnavailableOnConnectionRefusedAndTimeout() throws Exception {
        int unusedPort = findClosedPort();
        AiProperties unreachableProps = new AiProperties(
                "http://127.0.0.1:" + unusedPort,
                Duration.ofMillis(500),
                Duration.ofMillis(500),
                ""
        );
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(unreachableProps.connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        AiServiceClient unreachable = new AiServiceClient(client, unreachableProps, objectMapper);

        assertThat(unreachable.isReady()).isFalse();
        assertThatThrownBy(() -> unreachable.openChatStream("{}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AiUnavailableException.class)
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("Dịch vụ AI tạm thời không khả dụng. Vui lòng thử lại sau.");

        AiProperties timeoutProps = new AiProperties(
                "http://127.0.0.1:" + port + "/slow",
                Duration.ofMillis(500),
                Duration.ofMillis(100),
                ""
        );
        AiServiceClient slowClient = new AiServiceClient(client, timeoutProps, objectMapper);
        JsonNode payload = objectMapper.readTree("{\"query\":\"slow\"}");
        assertThatThrownBy(() -> slowClient.discover(payload))
                .isInstanceOf(AiUnavailableException.class)
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("Dịch vụ AI tạm thời không khả dụng. Vui lòng thử lại sau.");
    }

    private static int findClosedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void writeJson(com.sun.net.httpserver.HttpExchange exchange, int status, String json)
            throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
