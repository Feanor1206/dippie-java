package com.dippie.controller.assistant;

import com.dippie.config.assistant.AssistantProperties;
import com.dippie.dto.assistant.ChatDonePayload;
import com.dippie.dto.assistant.ChatEvent;
import com.dippie.dto.assistant.ChatMetadata;
import com.dippie.exception.ChatCapacityException;
import com.dippie.exception.ChatPayloadTooLargeException;
import com.dippie.exception.RequestBodyTooLargeException;
import com.dippie.exception.ServiceUnavailableException;
import com.dippie.security.ClientIpResolver;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.assistant.ChatRequestValidator;
import com.dippie.service.assistant.ChatService;
import com.dippie.service.assistant.ChatStreamGuard;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

// POST /api/chat đọc body có trần dung lượng, check quá tải trước rồi mới mở luồng SSE về frontend
@RestController
public class LocalChatController {

    public static final int MAX_REQUEST_BYTES = 256 * 1024;
    private static final String PAYLOAD_TOO_LARGE_MESSAGE = RequestBodyTooLargeException.DEFAULT_MESSAGE;

    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final ChatRequestValidator validator;
    private final ChatStreamGuard streamGuard;
    private final ClientIpResolver clientIpResolver;
    private final CurrentViewer currentViewer;
    private final int maxRequestBytes;

    public LocalChatController(
            ChatService chatService,
            ObjectMapper objectMapper,
            ChatStreamGuard streamGuard,
            ClientIpResolver clientIpResolver,
            CurrentViewer currentViewer,
            AssistantProperties properties
    ) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
        this.validator = new ChatRequestValidator(objectMapper);
        this.streamGuard = streamGuard;
        this.clientIpResolver = clientIpResolver;
        this.currentViewer = currentViewer;
        this.maxRequestBytes = properties == null
                ? MAX_REQUEST_BYTES
                : properties.chat().maxRequestBytes();
    }

    // phải đọc body, check chỉ mục và xin permit xong trước khi mở SSE thì lỗi 400/413/429/503 mới trả JSON sạch được
    @PostMapping("/api/chat")
    @SuppressWarnings("resource") // permit được đóng trong try-with-resources của lambda stream bên dưới
    public ResponseEntity<StreamingResponseBody> chat(HttpServletRequest request) throws IOException {
        // đọc body có trần dung lượng và check JSON trước kẻo request hỏng chiếm chỗ semaphore
        byte[] body = readBoundedBody(request);
        ChatRequestValidator.ValidatedChatRequest validated = validator.parseAndValidate(body);

        if (!chatService.isReady()) {
            throw new ServiceUnavailableException("Kho phim đang được cập nhật, bạn thử lại sau ít phút nhé.");
        }

        // xin permit trước khi trả StreamingResponseBody để lúc chạm trần nhảy qua handleCapacity trả 429 JSON kèm field detail
        String clientIp = clientIpResolver.resolve(request);
        Long viewerUserId = currentViewer.find().map(Viewer::userId).orElse(null);
        ChatStreamGuard.Permit permit = streamGuard.acquire(clientIp, viewerUserId);

        // Việt nhớ giữ permit trong try-with-resources này nhé, lỡ client ngắt giữa chừng thì permit vẫn trả lại semaphore
        HttpHeaders headers = buildSseHeaders();
        StreamingResponseBody streamBody = outputStream -> {
            try (permit; Stream<ChatEvent> events = chatService.stream(
                    validated.query(),
                    validated.messages()
            )) {
                Iterator<ChatEvent> iterator = events.iterator();
                while (iterator.hasNext()) {
                    ChatEvent event = iterator.next();
                    String frame = formatSseFrame(event);
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

    // trả lỗi validation đúng dạng JSON có field detail mà frontend chat đang parse
    @ExceptionHandler(ChatRequestValidator.ChatValidationException.class)
    public ResponseEntity<Map<String, String>> handleValidation(ChatRequestValidator.ChatValidationException exception) {
        return detailError(exception.status(), exception.getMessage());
    }

    // trả 429 JSON có field detail kèm Retry-After trước khi luồng SSE kịp mở
    @ExceptionHandler(ChatCapacityException.class)
    public ResponseEntity<Map<String, String>> handleCapacity(ChatCapacityException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.retryAfterSeconds()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("detail", exception.getMessage()));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleNotReady(ServiceUnavailableException exception) {
        return detailError(exception.status(), exception.getMessage());
    }

    private ResponseEntity<Map<String, String>> detailError(HttpStatus status, String detail) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("detail", detail));
    }

    // switch thẳng trên sealed interface ChatEvent để compiler nhắc liền nếu sau này thêm event mới
    private String formatSseFrame(ChatEvent event) throws IOException {
        Object payload = switch (event) {
            case ChatEvent.Metadata(ChatMetadata metadata) -> metadata;
            case ChatEvent.Token(String text) -> Map.of("text", text);
            case ChatEvent.Done(ChatDonePayload done) -> done;
            case ChatEvent.Error(String text) -> Map.of("text", text);
        };
        return "event: " + event.type() + "\ndata: " + objectMapper.writeValueAsString(payload) + "\n\n";
    }

    private HttpHeaders buildSseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE + "; charset=utf-8");
        headers.set(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        headers.set("X-Accel-Buffering", "no");
        return headers;
    }

    // check cả Content-Length lẫn số byte đọc thật phòng request gửi chunked không khai báo độ dài
    private byte[] readBoundedBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > maxRequestBytes) {
            throw new ChatPayloadTooLargeException(PAYLOAD_TOO_LARGE_MESSAGE);
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int total = 0;
        try (InputStream input = request.getInputStream()) {
            int bytesRead;
            while ((bytesRead = input.read(chunk)) != -1) {
                total += bytesRead;
                if (total > maxRequestBytes) {
                    throw new ChatPayloadTooLargeException(PAYLOAD_TOO_LARGE_MESSAGE);
                }
                buffer.write(chunk, 0, bytesRead);
            }
        }
        return buffer.toByteArray();
    }
}
