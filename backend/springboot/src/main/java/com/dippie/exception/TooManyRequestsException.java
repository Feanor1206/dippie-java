package com.dippie.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.Map;

// gọi vượt tần suất cho phép, trả 429 kèm sẵn header Retry-After cho client biết phải chờ bao lâu
public class TooManyRequestsException extends DomainException {

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, message, Map.of(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds)));
    }
}
