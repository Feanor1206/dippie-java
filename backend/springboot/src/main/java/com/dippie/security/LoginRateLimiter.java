package com.dippie.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Locale;

// rate limit đăng nhập gồm 2 cửa sổ tách riêng theo IP và theo tài khoản, chặn cả dò dàn trải lẫn dò từ nhiều IP
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

    // đếm theo IP trước rồi mới đếm theo tài khoản, trả số giây phải chờ hoặc 0 nếu chưa bị chặn
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
