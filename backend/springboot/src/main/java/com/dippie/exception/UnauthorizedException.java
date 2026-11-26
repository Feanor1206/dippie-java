package com.dippie.exception;

import org.springframework.http.HttpStatus;

// Lỗi chưa đăng nhập hoặc thông tin đăng nhập không hợp lệ, trả HTTP 401.
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
