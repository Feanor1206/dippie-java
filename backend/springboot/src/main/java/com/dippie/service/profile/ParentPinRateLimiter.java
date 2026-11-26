package com.dippie.service.profile;

import com.dippie.config.profile.ParentPinRateLimitProperties;
import com.dippie.exception.TooManyRequestsException;
import com.dippie.security.FixedWindowRateLimiter;

import org.springframework.stereotype.Component;

import java.time.Clock;

// sai PIN 5 lần thì khóa 5 phút cho đỡ dò, tách riêng bộ đếm theo từng tài khoản
@Component
public class ParentPinRateLimiter {

    private final FixedWindowRateLimiter delegate;

    public ParentPinRateLimiter(ParentPinRateLimitProperties properties, Clock clock) {
        this.delegate = new FixedWindowRateLimiter(
                properties.maxAttempts(),
                properties.window(),
                properties.maxClients(),
                clock
        );
    }

    // tài khoản đang bị khóa PIN thì ném 429 kèm Retry-After luôn khỏi chạy BCrypt
    public void ensureNotLocked(Long userId) {
        long retryAfter = delegate.blockedRetryAfterSeconds(keyOf(userId));
        if (retryAfter > 0) {
            throw new TooManyRequestsException(
                    "Bạn đã nhập sai mã PIN quá nhiều lần. Vui lòng thử lại sau ít phút.",
                    retryAfter
            );
        }
    }

    // ghi nhận 1 lần nhập sai PIN
    public void recordFailure(Long userId) {
        delegate.recordFailure(keyOf(userId));
    }

    // nhập đúng PIN khi chưa khóa thì xóa bộ đếm sai của tài khoản đó
    public void clear(Long userId) {
        delegate.clear(keyOf(userId));
    }

    // dọn sạch bộ đếm giữa các bài test
    public void reset() {
        delegate.reset();
    }

    private static String keyOf(Long userId) {
        return "pin:" + (userId == null ? "unknown" : userId);
    }
}
