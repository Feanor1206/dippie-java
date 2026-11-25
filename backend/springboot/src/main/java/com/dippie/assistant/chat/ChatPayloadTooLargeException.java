package com.dippie.assistant.chat;

import com.dippie.common.error.DomainException;
import org.springframework.http.HttpStatus;

public class ChatPayloadTooLargeException extends DomainException {

    public ChatPayloadTooLargeException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.PAYLOAD_TOO_LARGE;
    }
}
