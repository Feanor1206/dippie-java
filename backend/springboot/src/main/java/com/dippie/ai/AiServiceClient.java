package com.dippie.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

@Service
public class AiServiceClient {

    private static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final HttpClient httpClient;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    public AiServiceClient(HttpClient httpClient, AiProperties properties, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public InputStreamResponse openChatStream(byte[] requestBody) {
        byte[] payload = requestBody == null ? new byte[0] : requestBody;
        HttpRequest request = HttpRequest.newBuilder(properties.resolve("/api/chat"))
                .timeout(properties.requestTimeout())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String contentType = response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse("");
            return new InputStreamResponse(response.statusCode(), contentType, response.headers(), response.body());
        } catch (IOException exception) {
            throw new AiUnavailableException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException(exception);
        }
    }

    public JsonNode discover(JsonNode request) {
        return parseSuccessJson(forwardDiscover(request));
    }

    public JsonNode search(JsonNode request) {
        return parseSuccessJson(forwardSearch(request));
    }

    public JsonNode syncIndex(JsonNode request) {
        return parseSuccessJson(postJson("/api/index/sync", serialize(request), properties.serviceToken()));
    }

    public boolean isReady() {
        HttpRequest request = readinessRequest(properties.requestTimeout());
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    boolean isReadyForIndexSync(Duration remaining) {
        Duration timeout = remaining.compareTo(properties.requestTimeout()) < 0
                ? remaining : properties.requestTimeout();
        HttpRequest request = readinessRequest(timeout);
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                return true;
            }
            if (response.statusCode() != 503) {
                return false;
            }
            JsonNode status = objectMapper.readTree(response.body());
            return status != null && "ready".equals(status.path("database").textValue())
                    && "No accepted catalog index is available".equals(status.path("message").textValue());
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private HttpRequest readinessRequest(Duration timeout) {
        return HttpRequest.newBuilder(properties.resolve("/ready"))
                .timeout(timeout)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .GET()
                .build();
    }

    RawJsonResponse forwardDiscover(JsonNode request) {
        return postJson("/v1/discover", serialize(request), null);
    }

    RawJsonResponse forwardSearch(JsonNode request) {
        return postJson("/v1/search", serialize(request), null);
    }

    private RawJsonResponse postJson(String path, byte[] body, String serviceToken) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(properties.resolve(path))
                .timeout(properties.requestTimeout())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (serviceToken != null) {
            builder.header(SERVICE_TOKEN_HEADER, serviceToken);
        }
        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            String contentType = response.headers()
                    .firstValue(HttpHeaders.CONTENT_TYPE)
                    .filter(value -> !value.isBlank())
                    .orElse(MediaType.APPLICATION_JSON_VALUE);
            return new RawJsonResponse(response.statusCode(), contentType, response.headers(), response.body());
        } catch (IOException exception) {
            throw new AiUnavailableException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException(exception);
        }
    }

    private byte[] serialize(JsonNode request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize AI request body", exception);
        }
    }

    private JsonNode parseSuccessJson(RawJsonResponse response) {
        if (!response.isSuccessful()) {
            throw new AiUpstreamException(response.statusCode(), response.contentType(), response.body());
        }
        if (response.body() == null || response.body().length == 0) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new AiUnavailableException(exception);
        }
    }
}
