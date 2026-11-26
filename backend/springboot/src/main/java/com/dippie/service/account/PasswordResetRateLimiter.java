package com.dippie.service.account;

import com.dippie.config.account.PasswordResetRateLimitProperties;
import com.dippie.exception.TooManyRequestsException;
import com.dippie.security.FixedWindowRateLimiter;

import org.springframework.stereotype.Component;

import java.time.Clock;

// giới hạn số lần bấm quên mật khẩu theo IP qua FixedWindowRateLimiter kẻo bị spam mail
@Component
public class PasswordResetRateLimiter {

    private final FixedWindowRateLimiter delegate;

    public PasswordResetRateLimiter(PasswordResetRateLimitProperties properties, Clock clock) {
        this.delegate = new FixedWindowRateLimiter(
                properties.maxAttempts(),
                properties.window(),
                properties.maxClients(),
                clock
        );
    }

    // quá ngưỡng thì ném 429 kèm số giây chờ trong header Retry-After
    public void enforce(String clientIp) {
        String key = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp.trim();
        long retryAfter = delegate.retryAfterSeconds("reset:" + key);
        if (retryAfter > 0) {
            throw new TooManyRequestsException("Bạn vừa gửi quá nhiều yêu cầu đặt lại mật khẩu. Vui lòng đợi một lát rồi thử lại.", retryAfter);
        }
    }

    // xóa bộ đếm giữa các bài test cho khỏi dính trạng thái
    public void reset() {
        delegate.reset();
    }
}
