package com.dippie.service.play;

import com.dippie.security.JwtProperties;
import com.dippie.security.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

// test mã hóa AES-CTR + chữ ký HMAC phân tách theo loại token và chặn dùng lại hay sửa bậy token
class PlayTokenCodecTest {

    @Test
    void separatesTokenKindsRejectsTamperingAndEnforcesSingleUse() {
        byte[] rawSecret = new byte[32];
        for (int i = 0; i < rawSecret.length; i++) {
            rawSecret[i] = (byte) (i + 11);
        }
        Clock clock = Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"), ZoneOffset.UTC);
        MockEnvironment env = new MockEnvironment().withProperty("spring.profiles.active", "test");
        JwtService jwtService = new JwtService(
                new JwtProperties(Base64.getEncoder().encodeToString(rawSecret), Duration.ofHours(24), Duration.ofMinutes(5)),
                env,
                clock
        );
        PlayTokenCodec codec = new PlayTokenCodec(jwtService, clock);

        PlayTokenCodec.IssuedToken issued = codec.issue(
                PlayTokenCodec.KIND_LOOT_ANSWER,
                42L,
                Duration.ofSeconds(13),
                "19:2"
        );

        assertThat(codec.decode(issued.token(), PlayTokenCodec.KIND_LOOT_ANSWER))
                .isPresent()
                .get()
                .satisfies(decoded -> {
                    assertThat(decoded.profileId()).isEqualTo(42L);
                    assertThat(decoded.payload()).isEqualTo("19:2");
                    assertThat(decoded.expiresAt()).isEqualTo(Instant.parse("2026-11-26T10:00:13Z"));
                });

        // token loại rương báu không thể giải mã sang loại thuốc Dieppie hay ôn tập
        assertThat(codec.decode(issued.token(), PlayTokenCodec.KIND_MEDICINE_MATH)).isEmpty();
        assertThat(codec.decode(issued.token(), PlayTokenCodec.KIND_RECAP_QUIZ)).isEmpty();

        // sửa 1 ký tự là trượt HMAC ngay
        char flipped = issued.token().charAt(5) == 'A' ? 'B' : 'A';
        String tampered = issued.token().substring(0, 5) + flipped + issued.token().substring(6);
        assertThat(codec.decode(tampered, PlayTokenCodec.KIND_LOOT_ANSWER)).isEmpty();

        // redeem chỉ cho phép 1 lần
        Instant now = clock.instant();
        assertThat(codec.tryRedeem(issued.tokenId(), issued.expiresAt(), now)).isTrue();
        assertThat(codec.isUsed(issued.tokenId(), now)).isTrue();
        assertThat(codec.tryRedeem(issued.tokenId(), issued.expiresAt(), now)).isFalse();
    }
}
