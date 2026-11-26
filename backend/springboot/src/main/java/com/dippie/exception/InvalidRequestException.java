package com.dippie.exception;

import org.springframework.http.HttpStatus;

// Lỗi dữ liệu đầu vào sai quy tắc nghiệp vụ, trả HTTP 400.
public class InvalidRequestException extends DomainException {

    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
