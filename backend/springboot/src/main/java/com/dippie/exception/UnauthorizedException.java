package com.dippie.exception;

import org.springframework.http.HttpStatus;

// chưa đăng nhập hoặc thông tin đăng nhập sai, trả 401
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
