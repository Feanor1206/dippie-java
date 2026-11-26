package com.dippie.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

// Cấu hình ngưỡng và cửa sổ giới hạn tần suất theo IP và theo tài khoản, đọc từ dippie.auth.rate-limit.
@ConfigurationProperties("dippie.auth.rate-limit")
public record RateLimitProperties(
        int maxAttempts,
        Duration window,
        int maxClients,
        int accountMaxAttempts,
        Duration accountWindow
) {

    private static final int DEFAULT_IP_MAX_ATTEMPTS = 30;
    private static final Duration DEFAULT_IP_WINDOW = Duration.ofMinutes(1);
    private static final int DEFAULT_MAX_CLIENTS = 4096;
    private static final int DEFAULT_ACCOUNT_MAX_ATTEMPTS = 10;
    private static final Duration DEFAULT_ACCOUNT_WINDOW = Duration.ofMinutes(15);

    // Hàm tạo rút gọn cho test khi muốn dùng chung ngưỡng và cửa sổ cho cả IP lẫn tài khoản.
    public RateLimitProperties(int maxAttempts, Duration window, int maxClients) {
        this(maxAttempts, window, maxClients, maxAttempts, window);
    }

    @ConstructorBinding
    public RateLimitProperties {
        if (maxAttempts <= 0) {
            maxAttempts = DEFAULT_IP_MAX_ATTEMPTS;
        }
        if (window == null || window.isNegative() || window.isZero()) {
            window = DEFAULT_IP_WINDOW;
        }
        if (maxClients <= 0) {
            maxClients = DEFAULT_MAX_CLIENTS;
        }
        if (accountMaxAttempts <= 0) {
            accountMaxAttempts = DEFAULT_ACCOUNT_MAX_ATTEMPTS;
        }
        if (accountWindow == null || accountWindow.isNegative() || accountWindow.isZero()) {
            accountWindow = DEFAULT_ACCOUNT_WINDOW;
        }
    }
}
