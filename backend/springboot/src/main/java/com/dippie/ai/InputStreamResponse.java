package com.dippie.ai;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpHeaders;
import java.util.Optional;

public record InputStreamResponse(
        int statusCode,
        String contentType,
        HttpHeaders headers,
        InputStream body
) implements AutoCloseable {

    public Optional<String> firstHeader(String name) {
        return headers == null ? Optional.empty() : headers.firstValue(name);
    }

    @Override
    public void close() throws IOException {
        if (body != null) {
            body.close();
        }
    }
}
