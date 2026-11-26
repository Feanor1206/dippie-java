package com.dippie.security;

import com.dippie.security.JwtServiceTest.MutableClock;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

// test LoginRateLimiter đếm riêng theo IP với theo nick, spam key rác cũng không xóa mất bộ đếm
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

    // chặn kiểu 1 IP đi dò mật khẩu dàn trải qua nhiều nick
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
        // IP khác vẫn vào thử nick chưa bị khóa bình thường
        assertThat(limiter.retryAfterSeconds("10.0.0.2", "charlie")).isZero();
    }

    // chặn kiểu nhiều IP cùng xúm vào dò mật khẩu 1 nick
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

    // Việt để ý chỗ này, spam nick rác cho đầy bảng vẫn không đá được mục tiêu đang bị khóa ra khỏi RAM nha
    @Test
    void preventsLruEvictionBypassOfBlockedTargets() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(
                new RateLimitProperties(100, Duration.ofSeconds(60), 3, 2, Duration.ofMinutes(15)),
                clock
        );

        // cho nick "victim" chạm trần 2 lần thử trước
        assertThat(limiter.retryAfterSeconds("198.51.100.1", "victim")).isZero();
        assertThat(limiter.retryAfterSeconds("198.51.100.2", "victim")).isZero();
        assertThat(limiter.retryAfterSeconds("198.51.100.3", "victim")).isPositive();

        // bơm đống nick rác vượt maxClients = 3 để thử đẩy "victim" ra
        for (int i = 0; i < 20; i++) {
            limiter.retryAfterSeconds("198.51.100." + (10 + i), "dummy-" + i);
        }

        // "victim" vẫn phải nằm sổ đen, đừng để bị reset bộ đếm là toang
        assertThat(limiter.retryAfterSeconds("198.51.100.99", "victim")).isPositive();
        assertThat(limiter.trackedAccountCount()).isLessThanOrEqualTo(3);
    }
}
