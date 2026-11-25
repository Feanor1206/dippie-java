package com.dippie.assistant.search;

import com.dippie.common.error.RequestBodyTooLargeException;
import com.dippie.security.RequestBodySizeLimitFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// Controller REST mở chung hai đường dẫn /v1/search và /v1/discover chạy trên chỉ mục tìm kiếm cục bộ.
@RestController
class LocalDiscoverController {

    private final LocalCatalogSearch catalog;
    private final ObjectMapper mapper;

    LocalDiscoverController(LocalCatalogSearch catalog, ObjectMapper mapper) {
        this.catalog = catalog;
        this.mapper = mapper;
    }

    // Giải mã yêu cầu, kiểm tra chỉ mục đã sẵn sàng rồi truy vấn và đóng gói DiscoverResponse.
    @PostMapping({"/v1/search", "/v1/discover"})
    DiscoverResponse search(@RequestBody(required = false) JsonNode body) {
        var request = DiscoverRequest.parse(body);
        var index = catalog.index();
        if (index == null) {
            throw new DiscoverRequest.Rejection(HttpStatus.SERVICE_UNAVAILABLE,
                    "Kho phim đang được cập nhật, bạn thử lại sau ít phút nhé.");
        }
        var matches = catalog.matches(index, request.query(), request.limit(), request.constraints());
        return DiscoverResponse.of(request.query(), matches, mapper);
    }

    @ExceptionHandler(DiscoverRequest.Rejection.class)
    ResponseEntity<ErrorBody> rejected(DiscoverRequest.Rejection exception) {
        return ResponseEntity.status(exception.status()).body(new ErrorBody(exception.detail()));
    }

    @ExceptionHandler(RequestBodyTooLargeException.class)
    ResponseEntity<ErrorBody> payloadTooLarge(RequestBodyTooLargeException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ErrorBody(exception.getMessage()));
    }

    // Với request chunked vượt dung lượng, luồng đếm byte ném lỗi ngay khi Jackson đang đọc nên Spring bọc nó trong HttpMessageNotReadableException.
    // Phải tách kiểm tra 413 ở đây trước, nếu không client lại nhận nhầm 422 JSON lỗi.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException exception) {
        if (RequestBodySizeLimitFilter.containsTooLargeException(exception)) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(new ErrorBody(RequestBodyTooLargeException.DEFAULT_MESSAGE));
        }
        long offset = 0;
        if (exception.getCause() instanceof JsonProcessingException json && json.getLocation() != null) {
            offset = Math.max(0, json.getLocation().getByteOffset() - 1);
        }
        return ResponseEntity.unprocessableEntity().body(new ErrorBody(List.of(Map.of("type", "json_invalid",
                "loc", List.of("body", offset), "msg", "Không đọc được dữ liệu JSON gửi lên.", "input", Map.of(),
                "ctx", Map.of("error", "Dữ liệu gửi lên không đúng định dạng.")))));
    }

    // Khung trả lỗi thống nhất chỉ bọc trường detail.
    record ErrorBody(Object detail) {
    }
}
