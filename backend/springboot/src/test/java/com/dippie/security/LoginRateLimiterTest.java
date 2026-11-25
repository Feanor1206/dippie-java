package com.dippie.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

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

    @Test
    void evictsOldestClientAtCapacityWhileKeepingOtherAccountsTracked() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(
                new RateLimitProperties(1, Duration.ofSeconds(60), 2),
                clock
        );

        assertThat(limiter.retryAfterSeconds("127.0.0.1", "one")).isZero();
        assertThat(limiter.retryAfterSeconds("127.0.0.1", "two")).isZero();
        assertThat(limiter.retryAfterSeconds("127.0.0.1", "three")).isZero();
        assertThat(limiter.retryAfterSeconds("127.0.0.1", "two")).isPositive();
        assertThat(limiter.trackedClientCount()).isEqualTo(2);
    }

    @Test
    void isolatesDifferentClientIpsAndIdentifiers() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        LoginRateLimiter limiter = new LoginRateLimiter(
                new RateLimitProperties(1, Duration.ofSeconds(60), 10),
                clock
        );

        assertThat(limiter.retryAfterSeconds("10.0.0.1", "alice")).isZero();
        assertThat(limiter.retryAfterSeconds("10.0.0.1", "alice")).isPositive();
        assertThat(limiter.retryAfterSeconds("10.0.0.2", "alice")).isZero();
        assertThat(limiter.retryAfterSeconds("10.0.0.1", "bob")).isZero();
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant initial) {
            this.current = initial;
        }

        void advance(Duration duration) {
            this.current = this.current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
