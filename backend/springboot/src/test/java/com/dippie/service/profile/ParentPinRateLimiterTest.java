package com.dippie.service.profile;

import com.dippie.config.profile.ParentPinRateLimitProperties;
import com.dippie.exception.TooManyRequestsException;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// test khóa nhập sai PIN phụ huynh theo từng tài khoản và mở lại sau 5 phút
class ParentPinRateLimiterTest {

    @Test
    void locksAccountForFiveMinutesAfterFiveWrongAttemptsAndClearsOnSuccess() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-26T00:00:00Z"));
        ParentPinRateLimiter limiter = new ParentPinRateLimiter(
                new ParentPinRateLimitProperties(5, Duration.ofMinutes(5), 128),
                clock
        );

        // nhập sai 4 lần rồi nhập đúng thì phải xóa bộ đếm sai
        for (int attempt = 0; attempt < 4; attempt++) {
            limiter.ensureNotLocked(10L);
            limiter.recordFailure(10L);
        }
        limiter.clear(10L);

        // sai đủ 5 lần mới khóa trọn 5 phút kể từ lần sai thứ 5
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.ensureNotLocked(10L);
            limiter.recordFailure(10L);
            clock.advance(Duration.ofSeconds(10));
        }

        // lúc này tài khoản 10 đã bị khóa còn tài khoản 11 vẫn bình thường
        assertThatThrownBy(() -> limiter.ensureNotLocked(10L))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(ex -> {
                    TooManyRequestsException rateError = (TooManyRequestsException) ex;
                    assertThat(rateError.headers().get("Retry-After")).isNotEmpty();
                    assertThat(Long.parseLong(rateError.headers().get("Retry-After"))).isGreaterThan(250L);
                });
        assertThatCode(() -> limiter.ensureNotLocked(11L)).doesNotThrowAnyException();

        // tua đủ 5 phút là tự mở khóa
        clock.advance(Duration.ofMinutes(5));
        assertThatCode(() -> limiter.ensureNotLocked(10L)).doesNotThrowAnyException();
    }

    // đồng hồ giả cho test khóa PIN chạy tức thì khỏi ngồi đợi 5 phút thật
    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant initial) {
            this.current = initial;
        }

        private void advance(Duration duration) {
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
