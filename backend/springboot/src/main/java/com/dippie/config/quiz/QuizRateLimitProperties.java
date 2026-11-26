package com.dippie.config.quiz;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// giới hạn số lần trả lời sai câu hỏi theo từng hồ sơ bé, mặc định sai 10 lần khóa 10 phút
@ConfigurationProperties("dippie.quiz.rate-limit")
public record QuizRateLimitProperties(int maxAttempts, Duration window, int maxClients) {

    public QuizRateLimitProperties {
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
