package com.dippie.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int MIN_SECRET_BYTES = 32;
    private static final int MAX_SUBJECT_LENGTH = 50;
    private static final String ALGORITHM_HS256 = "HS256";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_PURPOSE = "purpose";
    private static final String CLAIM_ASSET_ID = "assetId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_VERSION = "ver";
    private static final String TYPE_PLAYBACK_GRANT = "playback_grant";
    private static final String PURPOSE_PLAYBACK = "playback";
    private static final String ROLE_ANONYMOUS = "ROLE_ANONYMOUS";
    private static final String ANONYMOUS_SUBJECT = "anonymous";
    private static final byte[] LEGACY_HEX_SECRET = Base64.getDecoder().decode(
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"
    );
    private static final byte[] LEGACY_TEXT_SECRET = Base64.getDecoder().decode(
            "dippieSecretKeyForJwtAuthentication2026MustBeLongEnoughForHmacSha256"
    );

    private final SecretKey signingKey;
    private final Duration expiration;
    private final Duration playbackGrantValidity;
    private final Clock clock;

    public JwtService(JwtProperties properties, Environment environment, Clock clock) {
        this.signingKey = resolveSigningKey(properties.secret(), environment);
        this.expiration = properties.expiration();
        this.playbackGrantValidity = properties.playbackGrantValidity();
        this.clock = clock;
    }

    public record LoginToken(String username, long tokenVersion) {
    }

    public String issueLoginToken(String username) {
        return issueLoginToken(username, 0L);
    }

    public String issueLoginToken(String username, long tokenVersion) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(expiration);
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_VERSION, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<LoginToken> parseLoginToken(String token) {
        Optional<Claims> parsed = parseVerifiedClaims(token);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        Claims claims = parsed.get();
        if (TYPE_PLAYBACK_GRANT.equals(claims.get(CLAIM_TYPE))
                || PURPOSE_PLAYBACK.equals(claims.get(CLAIM_PURPOSE))) {
            return Optional.empty();
        }
        String subject = claims.getSubject();
        if (subject == null || subject.isEmpty() || subject.length() > MAX_SUBJECT_LENGTH) {
            return Optional.empty();
        }
        Object rawVersion = claims.get(CLAIM_VERSION);
        if (!(rawVersion instanceof Integer || rawVersion instanceof Long)) {
            return Optional.empty();
        }
        long version = ((Number) rawVersion).longValue();
        if (version < 0) {
            return Optional.empty();
        }
        return Optional.of(new LoginToken(subject, version));
    }

    public Optional<String> loginSubject(String token) {
        return parseLoginToken(token).map(LoginToken::username);
    }

    public String issuePlaybackGrant(String assetId, String username, Role roleOrNull) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(playbackGrantValidity);
        String subject = (username == null || username.isEmpty()) ? ANONYMOUS_SUBJECT : username;
        String role = roleOrNull == null ? ROLE_ANONYMOUS : roleOrNull.name();
        return Jwts.builder()
                .subject(subject)
                .claim(CLAIM_ASSET_ID, assetId)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TYPE, TYPE_PLAYBACK_GRANT)
                .claim(CLAIM_PURPOSE, PURPOSE_PLAYBACK)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<String> playbackGrantRole(String token, String assetId) {
        if (assetId == null || assetId.isEmpty()) {
            return Optional.empty();
        }
        Optional<Claims> parsed = parseVerifiedClaims(token);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        Claims claims = parsed.get();
        if (!TYPE_PLAYBACK_GRANT.equals(claims.get(CLAIM_TYPE))) {
            return Optional.empty();
        }
        if (!assetId.equals(claims.get(CLAIM_ASSET_ID))) {
            return Optional.empty();
        }
        Object role = claims.get(CLAIM_ROLE);
        String resolved = role == null ? "" : role.toString();
        return Optional.of(resolved.isEmpty() ? ROLE_ANONYMOUS : resolved);
    }

    private Optional<Claims> parseVerifiedClaims(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(signingKey)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token);
            if (!ALGORITHM_HS256.equals(jws.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            Claims claims = jws.getPayload();
            if (claims.getSubject() == null || claims.getIssuedAt() == null || claims.getExpiration() == null) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static SecretKey resolveSigningKey(String configuredSecret, Environment environment) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("demo | test"))) {
                byte[] generated = new byte[MIN_SECRET_BYTES];
                new SecureRandom().nextBytes(generated);
                log.warn("APP_JWT_SECRET is blank; generated an ephemeral signing key for demo/test profile.");
                return Keys.hmacShaKeyFor(generated);
            }
            throw new IllegalStateException(
                    "APP_JWT_SECRET phải là khóa riêng, tối thiểu 32 byte; không dùng khóa mặc định cũ."
            );
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configuredSecret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET phải là khóa Base64 hợp lệ, tối thiểu 32 byte.",
                    ex
            );
        }
        if (decoded.length < MIN_SECRET_BYTES
                || Arrays.equals(decoded, LEGACY_HEX_SECRET)
                || Arrays.equals(decoded, LEGACY_TEXT_SECRET)) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET phải là khóa riêng, tối thiểu 32 byte; không dùng khóa mặc định cũ."
            );
        }
        return Keys.hmacShaKeyFor(decoded);
    }
}
