package com.dippie.common.error;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends DomainException {

    public InvalidRequestException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }
}
