package com.dippie.common.error;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

// Lỗi gọi vượt giới hạn tần suất, trả HTTP 429 kèm sẵn header Retry-After cho client biết cần chờ bao lâu.
public class TooManyRequestsException extends DomainException {

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, message, Map.of(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds)));
    }
}
