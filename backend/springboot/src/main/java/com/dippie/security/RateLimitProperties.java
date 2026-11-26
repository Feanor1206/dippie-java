package com.dippie.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

// cấu hình ngưỡng và cửa sổ rate limit theo IP với tài khoản, đọc từ dippie.auth.rate-limit
@ConfigurationProperties("dippie.auth.rate-limit")
public record RateLimitProperties(
        int maxAttempts,
        Duration window,
        int maxClients,
        int accountMaxAttempts,
        Duration accountWindow
) {

    // constructor gọn cho test khi muốn dùng chung ngưỡng và cửa sổ cho cả IP lẫn tài khoản
    public RateLimitProperties(int maxAttempts, Duration window, int maxClients) {
        this(maxAttempts, window, maxClients, maxAttempts, window);
    }

    @ConstructorBinding
    public RateLimitProperties {
        if (maxAttempts <= 0) {
            maxAttempts = 30;
        }
        if (window == null || window.isNegative() || window.isZero()) {
            window = Duration.ofMinutes(1);
        }
        if (maxClients <= 0) {
            maxClients = 4096;
        }
        if (accountMaxAttempts <= 0) {
            accountMaxAttempts = 10;
        }
        if (accountWindow == null || accountWindow.isNegative() || accountWindow.isZero()) {
            accountWindow = Duration.ofMinutes(15);
        }
    }
}
