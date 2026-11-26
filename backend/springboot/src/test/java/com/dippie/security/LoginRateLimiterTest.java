package com.dippie.security;

import com.dippie.security.JwtServiceTest.MutableClock;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// Test đơn vị cho LoginRateLimiter: giới hạn độc lập theo IP, theo tài khoản và không bị xóa bộ đếm khi spam key rác.
class LoginRateLimiterTest {

    @Test
    void limitsAttemptsAndRecoversAfterWindowElapses() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(
                new RateLimitProperties(2, Duration.ofSeconds(60), 100),
                clock
        );

        assertThat(limiter.retryAfterSeconds("127.0.0.1", "Alice")).isZero();
        assertThat(limiter.retryAfterSeconds("127.0.0.1", "  alice  ")).isZero();
        assertThat(limiter.retryAfterSeconds("127.0.0.1", "ALICE")).isPositive();

        clock.advance(Duration.ofSeconds(61));

        assertThat(limiter.retryAfterSeconds("127.0.0.1", "alice")).isZero();
    }

    // Chặn kiểu dò mật khẩu dàn trải từ một IP sang nhiều tài khoản khác nhau.
    @Test
    void blocksPasswordSprayingAcrossDifferentUsernamesFromSameIp() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(
                new RateLimitProperties(2, Duration.ofSeconds(60), 100, 10, Duration.ofMinutes(15)),
                clock
        );

        assertThat(limiter.retryAfterSeconds("10.0.0.1", "alice")).isZero();
        assertThat(limiter.retryAfterSeconds("10.0.0.1", "bob")).isZero();
        assertThat(limiter.retryAfterSeconds("10.0.0.1", "charlie")).isPositive();
        // IP khác vẫn thử được tài khoản chưa bị khóa.
        assertThat(limiter.retryAfterSeconds("10.0.0.2", "charlie")).isZero();
    }

    // Chặn kiểu dò mật khẩu phân tán vào cùng một tài khoản từ nhiều IP khác nhau.
    @Test
    void blocksDistributedBruteForceAgainstSingleAccountAcrossDifferentIps() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(
                new RateLimitProperties(30, Duration.ofSeconds(60), 100, 2, Duration.ofMinutes(15)),
                clock
        );

        assertThat(limiter.retryAfterSeconds("198.51.100.1", "victim")).isZero();
        assertThat(limiter.retryAfterSeconds("198.51.100.2", "VICTIM")).isZero();
        assertThat(limiter.retryAfterSeconds("198.51.100.3", "victim")).isPositive();
    }

    // Kẻ tấn công đẩy hàng loạt tài khoản rác vào đầy bảng vẫn không thể đá mục tiêu đang bị khóa ra khỏi bộ nhớ.
    @Test
    void preventsLruEvictionBypassOfBlockedTargets() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(
                new RateLimitProperties(100, Duration.ofSeconds(60), 3, 2, Duration.ofMinutes(15)),
                clock
        );

        // Cho tài khoản "victim" chạm trần 2 lần thử.
        assertThat(limiter.retryAfterSeconds("198.51.100.1", "victim")).isZero();
        assertThat(limiter.retryAfterSeconds("198.51.100.2", "victim")).isZero();
        assertThat(limiter.retryAfterSeconds("198.51.100.3", "victim")).isPositive();

        // Spam nhiều tài khoản giả vượt sức chứa maxClients = 3 để thử đẩy "victim" ra.
        for (int i = 0; i < 20; i++) {
            limiter.retryAfterSeconds("198.51.100." + (10 + i), "dummy-" + i);
        }

        // "victim" vẫn phải tiếp tục bị chặn thay vì được reset bộ đếm.
        assertThat(limiter.retryAfterSeconds("198.51.100.99", "victim")).isPositive();
        assertThat(limiter.trackedAccountCount()).isLessThanOrEqualTo(3);
    }
}
