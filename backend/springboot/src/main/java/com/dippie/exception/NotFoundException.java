package com.dippie.exception;

import org.springframework.http.HttpStatus;

// không tìm thấy dữ liệu cần lấy, trả 404
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
