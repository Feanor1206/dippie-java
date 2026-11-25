package com.dippie.common.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
