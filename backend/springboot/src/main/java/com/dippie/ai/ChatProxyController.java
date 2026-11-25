package com.dippie.ai;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
public class ChatProxyController {

    static final int MAX_REQUEST_BYTES = 1024 * 1024;
    private static final int STREAM_BUFFER_SIZE = 4096;
    private static final int MAX_ERROR_BODY_BYTES = 1024 * 1024;
    private static final String PAYLOAD_TOO_LARGE_MESSAGE = "Yêu cầu quá lớn.";
    private static final String CACHE_CONTROL_VALUE = "no-cache, no-transform";
    private static final String ACCEL_BUFFERING_HEADER = "X-Accel-Buffering";

    private final AiServiceClient aiServiceClient;

    public ChatProxyController(AiServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

    @PostMapping("/api/chat")
    public ResponseEntity<StreamingResponseBody> chat(HttpServletRequest request) throws IOException {
        byte[] body = readBoundedBody(request);
        InputStreamResponse upstream = aiServiceClient.openChatStream(body);
        if (upstream.statusCode() != HttpStatus.OK.value()) {
            return passthroughError(upstream);
        }
        HttpHeaders headers = buildStreamHeaders(upstream);
        StreamingResponseBody streamBody = outputStream -> {
            try (InputStreamResponse resource = upstream; InputStream input = resource.body()) {
                byte[] buffer = new byte[STREAM_BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                }
            }
        };
        return ResponseEntity.status(upstream.statusCode())
                .headers(headers)
                .body(streamBody);
    }

    private byte[] readBoundedBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
            throw new PayloadTooLargeException(PAYLOAD_TOO_LARGE_MESSAGE);
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[STREAM_BUFFER_SIZE];
        int total = 0;
        try (InputStream input = request.getInputStream()) {
            int bytesRead;
            while ((bytesRead = input.read(chunk)) != -1) {
                total += bytesRead;
                if (total > MAX_REQUEST_BYTES) {
                    throw new PayloadTooLargeException(PAYLOAD_TOO_LARGE_MESSAGE);
                }
                buffer.write(chunk, 0, bytesRead);
            }
        }
        return buffer.toByteArray();
    }

    private ResponseEntity<StreamingResponseBody> passthroughError(InputStreamResponse upstream) throws IOException {
        byte[] errorBody;
        try (InputStreamResponse resource = upstream; InputStream input = resource.body()) {
            errorBody = readUpTo(input, MAX_ERROR_BODY_BYTES);
        }
        HttpHeaders headers = new HttpHeaders();
        String contentType = upstream.contentType() == null || upstream.contentType().isBlank()
                ? MediaType.APPLICATION_JSON_VALUE
                : upstream.contentType();
        headers.set(HttpHeaders.CONTENT_TYPE, contentType);
        headers.setContentLength(errorBody.length);
        upstream.firstHeader(HttpHeaders.RETRY_AFTER)
                .ifPresent(value -> headers.set(HttpHeaders.RETRY_AFTER, value));
        StreamingResponseBody streamBody = outputStream -> {
            outputStream.write(errorBody);
            outputStream.flush();
        };
        return ResponseEntity.status(upstream.statusCode())
                .headers(headers)
                .body(streamBody);
    }

    private HttpHeaders buildStreamHeaders(InputStreamResponse upstream) {
        HttpHeaders headers = new HttpHeaders();
        String contentType = upstream.contentType() == null || upstream.contentType().isBlank()
                ? MediaType.TEXT_EVENT_STREAM_VALUE
                : upstream.contentType();
        headers.set(HttpHeaders.CONTENT_TYPE, contentType);
        headers.set(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE);
        headers.set(ACCEL_BUFFERING_HEADER, "no");
        upstream.firstHeader(HttpHeaders.RETRY_AFTER)
                .ifPresent(value -> headers.set(HttpHeaders.RETRY_AFTER, value));
        return headers;
    }

    private byte[] readUpTo(InputStream input, int maxBytes) throws IOException {
        if (input == null) {
            return new byte[0];
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] chunk = new byte[STREAM_BUFFER_SIZE];
        int remaining = maxBytes;
        int bytesRead;
        while (remaining > 0 && (bytesRead = input.read(chunk, 0, Math.min(chunk.length, remaining))) != -1) {
            output.write(chunk, 0, bytesRead);
            remaining -= bytesRead;
        }
        return output.toByteArray();
    }
}
