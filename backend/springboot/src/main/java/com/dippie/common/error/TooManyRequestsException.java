package com.dippie.common.error;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends DomainException {

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message, Map.of(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds)));
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }
}
