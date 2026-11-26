package com.dippie.exception;

import org.springframework.http.HttpStatus;

// Lỗi xung đột dữ liệu (ví dụ trùng tên đăng nhập hoặc email), trả HTTP 409.
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
