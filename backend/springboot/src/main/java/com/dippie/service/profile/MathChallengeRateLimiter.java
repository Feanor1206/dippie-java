package com.dippie.service.profile;

import com.dippie.config.profile.MathChallengeRateLimitProperties;
import com.dippie.exception.TooManyRequestsException;
import com.dippie.security.FixedWindowRateLimiter;

import org.springframework.stereotype.Component;

import java.time.Clock;

// giới hạn số lần trả lời sai bài toán theo từng hồ sơ bé, mặc định sai 10 lần khóa 10 phút
@Component
public class MathChallengeRateLimiter {

    private final FixedWindowRateLimiter delegate;

    public MathChallengeRateLimiter(MathChallengeRateLimitProperties properties, Clock clock) {
        this.delegate = new FixedWindowRateLimiter(
                properties.maxAttempts(),
                properties.window(),
                properties.maxClients(),
                clock
        );
    }

    // đang bị khóa do sai nhiều quá thì trả 429 kèm Retry-After luôn
    public void ensureNotLocked(Long profileId) {
        long retryAfter = delegate.blockedRetryAfterSeconds(keyOf(profileId));
        if (retryAfter > 0) {
            throw new TooManyRequestsException(
                    "Bé trả lời sai nhiều lần quá rồi, nghỉ vài phút rồi thử lại nhé.",
                    retryAfter
            );
        }
    }

    // cộng 1 lần trả lời sai cho hồ sơ bé
    public void recordFailure(Long profileId) {
        delegate.recordFailure(keyOf(profileId));
    }

    // dọn sạch bộ đếm giữa các bài test
    public void reset() {
        delegate.reset();
    }

    private static String keyOf(Long profileId) {
        return "math:" + (profileId == null ? "unknown" : profileId);
    }
}
