package com.dippie.exception;

import org.springframework.http.HttpStatus;

// Lỗi không tìm thấy dữ liệu được yêu cầu, trả HTTP 404.
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
