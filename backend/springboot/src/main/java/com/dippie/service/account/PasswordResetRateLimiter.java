package com.dippie.service.account;

import com.dippie.config.account.PasswordResetRateLimitProperties;
import com.dippie.exception.TooManyRequestsException;
import com.dippie.security.FixedWindowRateLimiter;

import org.springframework.stereotype.Component;

import java.time.Clock;

// Giới hạn số lần yêu cầu quên mật khẩu theo IP bằng FixedWindowRateLimiter để tránh spam email.
@Component
public class PasswordResetRateLimiter {

    private static final String LIMIT_EXCEEDED_MESSAGE =
            "Bạn vừa gửi quá nhiều yêu cầu đặt lại mật khẩu. Vui lòng đợi một lát rồi thử lại.";

    private final FixedWindowRateLimiter delegate;

    public PasswordResetRateLimiter(PasswordResetRateLimitProperties properties, Clock clock) {
        this.delegate = new FixedWindowRateLimiter(
                properties.maxAttempts(),
                properties.window(),
                properties.maxClients(),
                clock
        );
    }

    // Vượt ngưỡng thì trả 429 kèm số giây cần chờ trong header Retry-After.
    public void enforce(String clientIp) {
        String key = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp.trim();
        long retryAfter = delegate.retryAfterSeconds("reset:" + key);
        if (retryAfter > 0) {
            throw new TooManyRequestsException(LIMIT_EXCEEDED_MESSAGE, retryAfter);
        }
    }

    // Xóa bộ đếm giữa các bài test để tránh dính trạng thái.
    public void reset() {
        delegate.reset();
    }
}
