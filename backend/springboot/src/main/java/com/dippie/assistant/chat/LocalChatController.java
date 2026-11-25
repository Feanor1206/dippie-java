package com.dippie.assistant.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
class LocalChatController {

    static final int MAX_REQUEST_BYTES = 1024 * 1024;
    private static final String NOT_READY_DETAIL = "Chưa có snapshot catalog được chấp nhận.";
    private static final int READ_BUFFER_SIZE = 4096;
    private static final String PAYLOAD_TOO_LARGE_MESSAGE = "Yêu cầu quá lớn.";
    private static final String CACHE_CONTROL_VALUE = "no-cache, no-transform";
    private static final String ACCEL_BUFFERING_HEADER = "X-Accel-Buffering";

    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final ChatRequestValidator validator;

    LocalChatController(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
        this.validator = new ChatRequestValidator(objectMapper);
    }

    @PostMapping("/api/chat")
    ResponseEntity<StreamingResponseBody> chat(HttpServletRequest request) throws IOException {
        byte[] body = readBoundedBody(request);
        ChatRequestValidator.ValidatedChatRequest validated = validator.parseAndValidate(body);

        if (!chatService.isReady()) {
            throw new ChatNotReadyException(NOT_READY_DETAIL);
        }

        HttpHeaders headers = buildSseHeaders();
        StreamingResponseBody streamBody = outputStream -> {
            try (Stream<ChatEvent> events = chatService.stream(
                    validated.query(),
                    validated.messages()
            )) {
                Iterator<ChatEvent> iterator = events.iterator();
                while (iterator.hasNext()) {
                    ChatEvent event = iterator.next();
                    String frame = "event: " + event.type() + "\ndata: "
                            + objectMapper.writeValueAsString(event.data()) + "\n\n";
                    outputStream.write(frame.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                }
            } catch (IOException ignored) {
            }
        };
        return ResponseEntity.ok()
                .headers(headers)
                .body(streamBody);
    }

    @ExceptionHandler(ChatRequestValidator.ChatValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(ChatRequestValidator.ChatValidationException exception) {
        return rawDetailError(exception.status(), exception.getMessage());
    }

    @ExceptionHandler(ChatNotReadyException.class)
    public ResponseEntity<Map<String, String>> handleNotReady(ChatNotReadyException exception) {
        return rawDetailError(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    private ResponseEntity<Map<String, String>> rawDetailError(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("detail", detail));
    }

    private HttpHeaders buildSseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE + "; charset=utf-8");
        headers.set(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL_VALUE);
        headers.set(ACCEL_BUFFERING_HEADER, "no");
        return headers;
    }

    private byte[] readBoundedBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
            throw new ChatPayloadTooLargeException(PAYLOAD_TOO_LARGE_MESSAGE);
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[READ_BUFFER_SIZE];
        int total = 0;
        try (InputStream input = request.getInputStream()) {
            int bytesRead;
            while ((bytesRead = input.read(chunk)) != -1) {
                total += bytesRead;
                if (total > MAX_REQUEST_BYTES) {
                    throw new ChatPayloadTooLargeException(PAYLOAD_TOO_LARGE_MESSAGE);
                }
                buffer.write(chunk, 0, bytesRead);
            }
        }
        return buffer.toByteArray();
    }

    private static final class ChatNotReadyException extends RuntimeException {

        private ChatNotReadyException(String detail) {
            super(detail);
        }
    }
}
