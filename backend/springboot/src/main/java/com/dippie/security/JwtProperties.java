package com.dippie.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Cấu hình khóa ký và thời hạn sống của token đăng nhập với vé phát video, đọc từ dippie.jwt.
@ConfigurationProperties("dippie.jwt")
public record JwtProperties(String secret, Duration expiration, Duration playbackGrantValidity) {

    public JwtProperties {
        if (expiration == null) {
            expiration = Duration.ofHours(24);
        }
        if (playbackGrantValidity == null) {
            playbackGrantValidity = Duration.ofMinutes(5);
        }
    }
}
