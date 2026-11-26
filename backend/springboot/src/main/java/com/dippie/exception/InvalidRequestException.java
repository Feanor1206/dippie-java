package com.dippie.exception;

import org.springframework.http.HttpStatus;

// dữ liệu đầu vào sai quy tắc nghiệp vụ, trả 400
public class InvalidRequestException extends DomainException {

    public InvalidRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
