package com.dippie.common.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

public abstract class DomainException extends RuntimeException {

    private final transient Map<String, String> headers;

    protected DomainException(String message) {
        this(message, Map.of());
    }

    protected DomainException(String message, Map<String, String> headers) {
        super(message);
        this.headers = Map.copyOf(headers);
    }

    public abstract HttpStatus status();

    public Map<String, String> headers() {
        return headers;
    }
}
