package com.dippie.config.profile;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// cấu hình giới hạn số lần trả lời sai bài toán theo hồ sơ bé, mặc định 10 lần trong 10 phút
@ConfigurationProperties("dippie.math-challenge.rate-limit")
public record MathChallengeRateLimitProperties(int maxAttempts, Duration window, int maxClients) {

    public MathChallengeRateLimitProperties {
        if (maxAttempts <= 0) {
            maxAttempts = 10;
        }
        if (window == null || window.isNegative() || window.isZero()) {
            window = Duration.ofMinutes(10);
        }
        if (maxClients <= 0) {
            maxClients = 4096;
        }
    }
}
