package com.dippie.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

// Lớp cha trừu tượng cho mọi lỗi nghiệp vụ trong Dippie. Các lớp con kế thừa và gán sẵn HTTP status cùng header riêng,
// nhờ đó GlobalExceptionHandler chỉ cần bắt một chỗ là xử lý được hết.
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final transient Map<String, String> headers;

    protected DomainException(HttpStatus status, String message) {
        this(status, message, Map.of());
    }

    protected DomainException(HttpStatus status, String message, Map<String, String> headers) {
        super(message);
        this.status = status;
        this.headers = Map.copyOf(headers);
    }

    public HttpStatus status() {
        return status;
    }

    public Map<String, String> headers() {
        return headers;
    }
}
