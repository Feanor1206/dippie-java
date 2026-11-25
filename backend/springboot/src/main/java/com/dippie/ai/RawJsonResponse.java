package com.dippie.ai;

import java.net.http.HttpHeaders;
import java.util.Optional;

public record RawJsonResponse(
        int statusCode,
        String contentType,
        HttpHeaders headers,
        byte[] body
) {

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }

    public Optional<String> firstHeader(String name) {
        return headers == null ? Optional.empty() : headers.firstValue(name);
    }
}
