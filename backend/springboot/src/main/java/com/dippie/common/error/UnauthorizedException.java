package com.dippie.common.error;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNAUTHORIZED;
    }
}
