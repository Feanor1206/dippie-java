package com.dippie.controller.assistant;

import com.dippie.dto.assistant.DiscoverErrorBody;
import com.dippie.dto.assistant.DiscoverRequest;
import com.dippie.dto.assistant.DiscoverResponse;
import com.dippie.exception.RequestBodyTooLargeException;
import com.dippie.security.RequestBodySizeLimitFilter;
import com.dippie.service.assistant.DiscoverService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// controller mở chung 2 route /v1/search và /v1/discover chạy trên chỉ mục tìm kiếm local
@RestController
public class LocalDiscoverController {

    private final DiscoverService discoverService;

    public LocalDiscoverController(DiscoverService discoverService) {
        this.discoverService = discoverService;
    }

    // chỉ parse request rồi đẩy xuống DiscoverService, kết quả trả thẳng ra giao diện
    @PostMapping({"/v1/search", "/v1/discover"})
    public DiscoverResponse search(@RequestBody(required = false) JsonNode body) {
        return discoverService.discover(DiscoverRequest.parse(body));
    }

    @ExceptionHandler(DiscoverRequest.Rejection.class)
    public ResponseEntity<DiscoverErrorBody> rejected(DiscoverRequest.Rejection exception) {
        return ResponseEntity.status(exception.status()).body(new DiscoverErrorBody(exception.detail()));
    }

    @ExceptionHandler(RequestBodyTooLargeException.class)
    public ResponseEntity<DiscoverErrorBody> payloadTooLarge(RequestBodyTooLargeException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new DiscoverErrorBody(exception.getMessage()));
    }

    // request chunked vượt dung lượng sẽ văng lỗi lúc Jackson đang đọc nên Spring bọc trong HttpMessageNotReadableException
    // chỗ này phải tách check 413 trước kẻo client nhận nhầm 422 JSON lỗi
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<DiscoverErrorBody> unreadable(HttpMessageNotReadableException exception) {
        if (RequestBodySizeLimitFilter.containsTooLargeException(exception)) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(new DiscoverErrorBody(RequestBodyTooLargeException.DEFAULT_MESSAGE));
        }
        long offset = 0;
        if (exception.getCause() instanceof JsonProcessingException json && json.getLocation() != null) {
            offset = Math.max(0, json.getLocation().getByteOffset() - 1);
        }
        return ResponseEntity.unprocessableEntity().body(new DiscoverErrorBody(List.of(Map.of("type", "json_invalid",
                "loc", List.of("body", offset), "msg", "Không đọc được dữ liệu JSON gửi lên.", "input", Map.of(),
                "ctx", Map.of("error", "Dữ liệu gửi lên không đúng định dạng.")))));
    }

}
