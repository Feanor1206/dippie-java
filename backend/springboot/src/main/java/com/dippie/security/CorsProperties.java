package com.dippie.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// Danh sách origin được phép gọi API từ trình duyệt qua CORS, đọc từ dippie.cors.
@ConfigurationProperties("dippie.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream()
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toList();
    }
}
