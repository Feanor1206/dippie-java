package com.dippie.common.error;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends DomainException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}
