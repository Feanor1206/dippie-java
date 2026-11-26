package com.dippie.exception;

import org.springframework.http.HttpStatus;

// Lỗi khi tính năng hoặc tài nguyên phụ thuộc tạm chưa sẵn sàng, trả HTTP 503.
public class ServiceUnavailableException extends DomainException {

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
