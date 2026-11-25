package com.dippie.common.error;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.FORBIDDEN;
    }
}
