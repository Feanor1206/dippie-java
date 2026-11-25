package com.dippie.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class JwtServiceTest {

    private static final String VALID_SECRET = Base64.getEncoder().encodeToString(sequentialBytes(32));
    private static final String LEGACY_HEX_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final String LEGACY_TEXT_SECRET = "dippieSecretKeyForJwtAuthentication2026MustBeLongEnoughForHmacSha256";

    @Test
    void issuesAndParsesLoginToken() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        JwtService service = createService(VALID_SECRET, clock, "mysql");

        String token = service.issueLoginToken("alice");

        assertThat(service.loginSubject(token)).contains("alice");
        assertThat(service.parseLoginToken(token)).contains(new JwtService.LoginToken("alice", 0L));
        assertThat(service.playbackGrantRole(token, "asset-1")).isEmpty();
    }

    @Test
    void issuesVersionedLoginTokenAndRejectsMissingOrInvalidVersionClaim() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        JwtService service = createService(VALID_SECRET, clock, "mysql");

        String versioned = service.issueLoginToken("alice", 7L);
        assertThat(service.parseLoginToken(versioned)).contains(new JwtService.LoginToken("alice", 7L));

        byte[] keyBytes = Base64.getDecoder().decode(VALID_SECRET);
        String withoutVersion = Jwts.builder()
                .subject("alice")
                .issuedAt(Date.from(clock.instant()))
                .expiration(Date.from(clock.instant().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(keyBytes), Jwts.SIG.HS256)
                .compact();
        String stringVersion = Jwts.builder()
                .subject("alice")
                .claim("ver", "1")
                .issuedAt(Date.from(clock.instant()))
                .expiration(Date.from(clock.instant().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(keyBytes), Jwts.SIG.HS256)
                .compact();
        String negativeVersion = Jwts.builder()
                .subject("alice")
                .claim("ver", -1)
                .issuedAt(Date.from(clock.instant()))
                .expiration(Date.from(clock.instant().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(keyBytes), Jwts.SIG.HS256)
                .compact();

        assertThat(service.parseLoginToken(withoutVersion)).isEmpty();
        assertThat(service.loginSubject(withoutVersion)).isEmpty();
        assertThat(service.parseLoginToken(stringVersion)).isEmpty();
        assertThat(service.parseLoginToken(negativeVersion)).isEmpty();
    }

    @Test
    void rejectsExpiredLoginToken() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        JwtService service = createService(VALID_SECRET, clock, "mysql");

        String token = service.issueLoginToken("alice");
        clock.advance(Duration.ofHours(25));

        assertThat(service.loginSubject(token)).isEmpty();
    }

    @Test
    void issuesAndValidatesPlaybackGrantAndRejectsItAsLoginToken() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        JwtService service = createService(VALID_SECRET, clock, "mysql");

        String userGrant = service.issuePlaybackGrant("asset-42", "alice", Role.ROLE_USER);
        String anonGrant = service.issuePlaybackGrant("asset-42", null, null);

        assertThat(service.playbackGrantRole(userGrant, "asset-42")).contains("ROLE_USER");
        assertThat(service.playbackGrantRole(userGrant, "asset-99")).isEmpty();
        assertThat(service.loginSubject(userGrant)).isEmpty();

        assertThat(service.playbackGrantRole(anonGrant, "asset-42")).contains("ROLE_ANONYMOUS");
        assertThat(service.loginSubject(anonGrant)).isEmpty();

        clock.advance(Duration.ofMinutes(6));
        assertThat(service.playbackGrantRole(userGrant, "asset-42")).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithDifferentSecretOrOverlongSubject() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        JwtService service = createService(VALID_SECRET, clock, "mysql");

        String forged = Jwts.builder()
                .subject("admin")
                .issuedAt(Date.from(clock.instant()))
                .expiration(Date.from(clock.instant().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(LEGACY_HEX_SECRET)), Jwts.SIG.HS256)
                .compact();
        String longSubjectToken = service.issueLoginToken("a".repeat(51));

        assertThat(service.loginSubject(forged)).isEmpty();
        assertThat(service.loginSubject(longSubjectToken)).isEmpty();
        assertThat(service.loginSubject("not-a-jwt")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            LEGACY_HEX_SECRET,
            "invalid!",
            "c2hvcnQ=",
            LEGACY_TEXT_SECRET
    })
    void rejectsMissingKnownShortOrInvalidSecretOutsideDemoAndTest(String secret) {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

        assertThatThrownBy(() -> createService(secret, clock, "mysql"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_JWT_SECRET");
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo", "test"})
    void generatesEphemeralKeyWhenSecretBlankInDemoOrTestProfile(String profile) {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        JwtService service = createService("", clock, profile);

        String token = service.issueLoginToken("demo");

        assertThat(service.loginSubject(token)).contains("demo");
    }

    private static JwtService createService(String secret, Clock clock, String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        JwtProperties properties = new JwtProperties(secret, Duration.ofHours(24), Duration.ofMinutes(5));
        return new JwtService(properties, environment, clock);
    }

    private static byte[] sequentialBytes(int length) {
        byte[] bytes = new byte[length];
        IntStream.range(0, length).forEach(index -> bytes[index] = (byte) index);
        return bytes;
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
