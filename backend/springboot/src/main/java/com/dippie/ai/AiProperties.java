package com.dippie.ai;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dippie.ai")
public record AiProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration requestTimeout,
        String serviceToken
) {

    public AiProperties {
        String normalizedBase = baseUrl == null || baseUrl.isBlank() ? "http://127.0.0.1:8000" : baseUrl.trim();
        while (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        baseUrl = normalizedBase;
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (requestTimeout == null) {
            requestTimeout = Duration.ofSeconds(60);
        }
        if (serviceToken == null) {
            serviceToken = "";
        }
    }

    public URI resolve(String path) {
        return URI.create(baseUrl + path);
    }
}
