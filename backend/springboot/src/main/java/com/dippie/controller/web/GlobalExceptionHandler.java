package com.dippie.controller.web;

import com.dippie.common.web.ApiResponse;
import com.dippie.exception.DomainException;
import com.dippie.exception.RequestBodyTooLargeException;
import com.dippie.security.RequestBodySizeLimitFilter;
import com.dippie.service.web.SpaIndex;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// Gom mọi lỗi phát sinh từ tầng controller về một chỗ để trả ApiResponse đồng bộ; nhờ kế thừa DomainException nên toàn bộ lỗi nghiệp vụ đi chung qua một nhánh xử lý.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final SpaIndex spaIndex;

    public GlobalExceptionHandler(SpaIndex spaIndex) {
        this.spaIndex = spaIndex;
    }

    // Tận dụng đa hình: mỗi lớp con của DomainException tự mang sẵn HTTP status và header riêng (như Retry-After), ở đây chỉ việc đọc ra trả về.
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomain(DomainException exception) {
        HttpHeaders headers = new HttpHeaders();
        exception.headers().forEach(headers::add);
        return ResponseEntity.status(exception.status())
                .headers(headers)
                .body(ApiResponse.failure(exception.getMessage()));
    }

    // Bắt lỗi body JSON thiếu hoặc sai cú pháp; nếu bên trong bị bọc RequestBodyTooLargeException thì trả đúng 413 thay vì 400.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException exception) {
        if (RequestBodySizeLimitFilter.containsTooLargeException(exception)) {
            return failure(HttpStatus.PAYLOAD_TOO_LARGE, RequestBodyTooLargeException.DEFAULT_MESSAGE);
        }
        return failure(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không đúng định dạng.");
    }

    // Lấy thông báo lỗi đầu tiên từ @Valid để trả ngắn gọn cho frontend.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Thông tin gửi lên chưa hợp lệ.");
        return failure(HttpStatus.BAD_REQUEST, message);
    }

    // Thiếu query param bắt buộc hoặc truyền sai kiểu dữ liệu.
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadParameter() {
        return failure(HttpStatus.BAD_REQUEST, "Tham số gửi lên chưa đúng.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod() {
        return failure(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức này không được hỗ trợ.");
    }

    // Trình duyệt xin HTML ở route lạ thì trả trang SPA kèm mã 404, còn API hoặc client xin JSON thì trả 404 dạng JSON.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<?> handleNoResource(HttpServletRequest request) {
        return spaIndex.fallback(request)
                .<ResponseEntity<?>>map(response -> response)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .varyBy(HttpHeaders.ACCEPT)
                        .body(ApiResponse.failure("Không tìm thấy nội dung này.")));
    }

    // Việt nhớ không log exception.getMessage() ở mức error vì thông điệp SQL/Hibernate dễ dính dữ liệu người dùng;
    // mình chỉ ghi tên lớp lỗi, khi cần soi stack trace thì bật mức debug.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Lỗi chưa xử lý khi nhận yêu cầu: {}", exception.getClass().getSimpleName());
        if (log.isDebugEnabled()) {
            log.debug("Chi tiết ngoại lệ chưa xử lý", exception);
        }
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, "Hệ thống đang gặp trục trặc, bạn thử lại sau nhé.");
    }

    private ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.failure(message));
    }
}
