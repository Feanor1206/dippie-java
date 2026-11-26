package com.dippie.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Locale;

// Bộ giới hạn tần suất đăng nhập với hai cửa sổ độc lập theo IP và theo tài khoản, chặn cả dò mật khẩu dàn trải lẫn dò phân tán từ nhiều IP.
@Component
public class LoginRateLimiter {

    private final FixedWindowRateLimiter ipLimiter;
    private final FixedWindowRateLimiter accountLimiter;

    public LoginRateLimiter(RateLimitProperties properties, Clock clock) {
        this.ipLimiter = new FixedWindowRateLimiter(
                properties.maxAttempts(),
                properties.window(),
                properties.maxClients(),
                clock
        );
        this.accountLimiter = new FixedWindowRateLimiter(
                properties.accountMaxAttempts(),
                properties.accountWindow(),
                properties.maxClients(),
                clock
        );
    }

    // Đếm lượt thử theo IP trước rồi mới đếm theo tài khoản; trả số giây cần chờ hoặc 0 nếu chưa bị chặn.
    public long retryAfterSeconds(String clientIp, String identifier) {
        String normalizedIp = normalizeIp(clientIp);
        long ipRetry = ipLimiter.retryAfterSeconds("ip:" + normalizedIp);
        if (ipRetry > 0) {
            return ipRetry;
        }
        String normalizedAccount = normalizeIdentifier(identifier);
        if (normalizedAccount.isEmpty()) {
            return 0L;
        }
        return accountLimiter.retryAfterSeconds("account:" + normalizedAccount);
    }

    public void reset() {
        ipLimiter.reset();
        accountLimiter.reset();
    }

    int trackedClientCount() {
        return ipLimiter.trackedCount();
    }

    int trackedAccountCount() {
        return accountLimiter.trackedCount();
    }

    private static String normalizeIp(String clientIp) {
        return (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp.trim();
    }

    private static String normalizeIdentifier(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
    }
}
