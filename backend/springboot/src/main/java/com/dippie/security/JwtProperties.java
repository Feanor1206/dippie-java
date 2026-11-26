package com.dippie.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// cấu hình khóa ký và hạn dùng của token đăng nhập với vé phát video, đọc từ dippie.jwt
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
