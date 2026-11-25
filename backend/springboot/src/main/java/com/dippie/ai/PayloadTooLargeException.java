package com.dippie.ai;

import com.dippie.common.error.DomainException;
import org.springframework.http.HttpStatus;

public class PayloadTooLargeException extends DomainException {

    public PayloadTooLargeException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.PAYLOAD_TOO_LARGE;
    }
}
