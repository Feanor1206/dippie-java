package com.dippie.service.profile;

import com.dippie.config.profile.MathChallengeRateLimitProperties;
import com.dippie.exception.TooManyRequestsException;
import com.dippie.security.JwtProperties;
import com.dippie.security.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// test mã hóa/ký HMAC token bài toán không lộ đáp án và giới hạn 10 lần sai trong 10 phút
class MathChallengeTokenCodecAndRateLimiterTest {

    @Test
    void signsOpaqueEncryptedChallengeTokenAndRejectsTamperingOrWrongSecret() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-26T08:00:00Z"));
        JwtService primaryJwt = createJwtService(sequentialSecret(1), clock);
        JwtService otherJwt = createJwtService(sequentialSecret(50), clock);
        MathChallengeTokenCodec codec = new MathChallengeTokenCodec(primaryJwt, clock);
        MathChallengeTokenCodec otherCodec = new MathChallengeTokenCodec(otherJwt, clock);

        MathChallengeTokenCodec.IssuedToken issued1 = codec.issue(42L, 73, 3);
        MathChallengeTokenCodec.IssuedToken issued2 = codec.issue(42L, 73, 3);

        // 2 token cùng đề nhưng khác IV và tokenId nên chuỗi phải khác nhau, giải mã base64 không lộ đáp án
        assertThat(issued1.token()).isNotEqualTo(issued2.token());
        assertThat(issued1.tokenId()).isNotEqualTo(issued2.tokenId());
        byte[] rawBytes = Base64.getUrlDecoder().decode(issued1.token());
        assertThat(new String(rawBytes, StandardCharsets.UTF_8)).doesNotContain("73").doesNotContain("42");

        MathChallengeTokenCodec.DecodedToken decoded = codec.decode(issued1.token()).orElseThrow();
        assertThat(decoded.tokenId()).isEqualTo(issued1.tokenId());
        assertThat(decoded.profileId()).isEqualTo(42L);
        assertThat(decoded.answer()).isEqualTo(73);
        assertThat(decoded.level()).isEqualTo(3);
        assertThat(decoded.expiresAt()).isEqualTo(Instant.parse("2026-11-26T08:02:00Z"));

        // đổi 1 byte bất kỳ trên ciphertext hoặc chữ ký HMAC hay dùng secret khác đều bị từ chối
        byte[] tampered = rawBytes.clone();
        tampered[20] ^= 0x01;
        String tamperedToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tampered);
        assertThat(codec.decode(tamperedToken)).isEmpty();
        assertThat(otherCodec.decode(issued1.token())).isEmpty();
        assertThat(codec.decode("not-a-valid-token")).isEmpty();
        assertThat(codec.decode("")).isEmpty();
    }

    @Test
    void isolatesScopedV2TokensFromRegularV1TokensAndRejectsWrongScope() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-26T08:00:00Z"));
        JwtService jwt = createJwtService(sequentialSecret(11), clock);
        MathChallengeTokenCodec codec = new MathChallengeTokenCodec(jwt, clock);

        MathChallengeTokenCodec.IssuedToken v1 = codec.issue(9L, 14, 2);
        MathChallengeTokenCodec.IssuedToken v2 = codec.issueScoped(9L, 36, 3, "vip:ep_1");

        // token v1 không mở được bằng decodeScoped và token v2 không mở được bằng decode v1
        assertThat(codec.decodeScoped(v1.token())).isEmpty();
        assertThat(codec.decode(v2.token())).isEmpty();

        MathChallengeTokenCodec.DecodedScopedToken decoded = codec.decodeScoped(v2.token()).orElseThrow();
        assertThat(decoded.tokenId()).isEqualTo(v2.tokenId());
        assertThat(decoded.profileId()).isEqualTo(9L);
        assertThat(decoded.answer()).isEqualTo(36);
        assertThat(decoded.level()).isEqualTo(3);
        assertThat(decoded.scope()).isEqualTo("vip:ep_1");
        assertThat(decoded.expiresAt()).isEqualTo(Instant.parse("2026-11-26T08:02:00Z"));

        // sửa 1 byte trên token v2 phải bị từ chối ngay
        byte[] raw = Base64.getUrlDecoder().decode(v2.token());
        raw[raw.length - 1] ^= 0x04;
        String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        assertThat(codec.decodeScoped(tampered)).isEmpty();
    }

    @Test
    void locksWrongMathAnswersAfterTenFailuresForTenMinutesPerProfile() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-26T08:00:00Z"));
        MathChallengeRateLimiter limiter = new MathChallengeRateLimiter(
                new MathChallengeRateLimitProperties(10, Duration.ofMinutes(10), 128),
                clock
        );

        for (int attempt = 0; attempt < 10; attempt++) {
            limiter.ensureNotLocked(7L);
            limiter.recordFailure(7L);
            clock.advance(Duration.ofSeconds(5));
        }

        // hồ sơ 7 bị khóa 429 kèm Retry-After còn hồ sơ 8 vẫn bình thường
        assertThatThrownBy(() -> limiter.ensureNotLocked(7L))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(ex -> {
                    TooManyRequestsException err = (TooManyRequestsException) ex;
                    assertThat(Long.parseLong(err.headers().get("Retry-After"))).isGreaterThan(500L);
                });
        assertThatCode(() -> limiter.ensureNotLocked(8L)).doesNotThrowAnyException();

        // tua đủ 10 phút là tự mở khóa
        clock.advance(Duration.ofMinutes(10));
        assertThatCode(() -> limiter.ensureNotLocked(7L)).doesNotThrowAnyException();
    }

    private static JwtService createJwtService(String secret, Clock clock) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("mysql");
        return new JwtService(new JwtProperties(secret, Duration.ofHours(24), Duration.ofMinutes(5)), env, clock);
    }

    private static String sequentialSecret(int offset) {
        byte[] bytes = new byte[32];
        IntStream.range(0, 32).forEach(index -> bytes[index] = (byte) (index + offset));
        return Base64.getEncoder().encodeToString(bytes);
    }

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
