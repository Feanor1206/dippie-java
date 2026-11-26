package com.dippie.service.quiz;

import com.dippie.config.quiz.QuizRateLimitProperties;
import com.dippie.exception.TooManyRequestsException;
import com.dippie.model.quiz.QuizChallengeContext;
import com.dippie.model.quiz.QuizGrade;
import com.dippie.model.quiz.QuizOptionKey;
import com.dippie.model.quiz.QuizSubject;
import com.dippie.security.JwtProperties;
import com.dippie.security.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// test mã hóa AES-CTR + chữ ký HMAC-SHA256, chống giả mạo, khóa dùng 1 lần và rate limit sai 10 lần khóa 10 phút
class QuizTokenCodecAndRateLimiterTest {

    @Test
    void encryptsVerifiesRejectsTamperingCrossContextAndEnforcesSingleUse() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-26T10:00:00Z"));
        String secret = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
        JwtService jwtService = new JwtService(
                new JwtProperties(secret, Duration.ofHours(1), Duration.ofMinutes(5)),
                new MockEnvironment(),
                clock
        );
        QuizTokenCodec codec = new QuizTokenCodec(jwtService, clock);

        QuizTokenCodec.IssuedToken issued = codec.issue(
                QuizChallengeContext.SCREEN_TIME,
                42L,
                "screen-time",
                Duration.ofMinutes(2),
                new QuizTokenCodec.TokenData(
                        10L,
                        99L,
                        QuizOptionKey.C,
                        QuizGrade.LOP_3,
                        QuizSubject.TIENG_ANH,
                        "q:99",
                        "What color is the sky?",
                        "Tiếng Anh từ vựng",
                        "Blue",
                        "Blue là màu xanh dương"
                )
        );

        assertThat(issued.token()).doesNotContain("Blue").doesNotContain("99");

        QuizTokenCodec.DecodedToken decoded = codec.decode(issued.token(), QuizChallengeContext.SCREEN_TIME)
                .orElseThrow();
        assertThat(decoded.profileId()).isEqualTo(42L);
        assertThat(decoded.correctOption()).isEqualTo(QuizOptionKey.C);
        assertThat(decoded.correctText()).isEqualTo("Blue");
        assertThat(decoded.grade()).isEqualTo(QuizGrade.LOP_3);

        // khác ngữ cảnh bị từ chối ngay
        assertThat(codec.decode(issued.token(), QuizChallengeContext.VIP_UNLOCK)).isEmpty();
        assertThat(codec.decode(issued.token(), QuizChallengeContext.LOOT_DROP)).isEmpty();

        // sửa 1 byte chữ ký bị từ chối
        char flipped = issued.token().charAt(issued.token().length() - 1) == 'A' ? 'B' : 'A';
        String tampered = issued.token().substring(0, issued.token().length() - 1) + flipped;
        assertThat(codec.decode(tampered, QuizChallengeContext.SCREEN_TIME)).isEmpty();

        // dùng 1 lần và tự dọn sau khi hết hạn
        assertThat(codec.tryRedeem(decoded.tokenId(), decoded.expiresAt(), clock.instant())).isTrue();
        assertThat(codec.tryRedeem(decoded.tokenId(), decoded.expiresAt(), clock.instant())).isFalse();
        assertThat(codec.trackedUsedTokenCount()).isEqualTo(1);

        // khi giao dịch lưu lỗi và gọi release thì gửi lại được đúng 1 lần, không nhân đôi lượt sau khi đã khóa lại
        codec.release(decoded.tokenId());
        assertThat(codec.trackedUsedTokenCount()).isZero();
        assertThat(codec.tryRedeem(decoded.tokenId(), decoded.expiresAt(), clock.instant())).isTrue();
        assertThat(codec.tryRedeem(decoded.tokenId(), decoded.expiresAt(), clock.instant())).isFalse();

        clock.advance(Duration.ofSeconds(121));
        assertThat(codec.trackedUsedTokenCount()).isZero();
    }

    @Test
    void rateLimiterBlocksAfterTenWrongAttemptsAndResetsAfterTenMinutes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-26T10:00:00Z"));
        QuizRateLimiter limiter = new QuizRateLimiter(new QuizRateLimitProperties(10, Duration.ofMinutes(10), 100), clock);

        for (int i = 0; i < 10; i++) {
            limiter.ensureNotLocked(7L);
            limiter.recordFailure(7L);
        }

        assertThatThrownBy(() -> limiter.ensureNotLocked(7L))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(ex -> assertThat(Long.parseLong(((TooManyRequestsException) ex).headers().get("Retry-After"))).isGreaterThan(0));

        // hồ sơ bé khác không bị ảnh hưởng
        limiter.ensureNotLocked(8L);

        // qua đủ 10 phút thì hết khóa
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));
        limiter.ensureNotLocked(7L);
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        MutableClock(Instant initial) {
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
