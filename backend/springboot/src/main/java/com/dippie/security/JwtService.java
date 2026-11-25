package com.dippie.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
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

// Xử lý ký và kiểm tra JWT (HS256) cho cả phiên đăng nhập lẫn vé phát video ngắn hạn.
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
    private static final String CLAIM_USER_ID = "uid";
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
    private final JwtParser jwtParser;
    private final Duration expiration;
    private final Duration playbackGrantValidity;
    private final Clock clock;

    public JwtService(JwtProperties properties, Environment environment, Clock clock) {
        this.signingKey = resolveSigningKey(properties.secret(), environment);
        this.expiration = properties.expiration();
        this.playbackGrantValidity = properties.playbackGrantValidity();
        this.clock = clock;
        this.jwtParser = Jwts.parser()
                .verifyWith(this.signingKey)
                .clock(() -> Date.from(this.clock.instant()))
                .build();
    }

    // Dữ liệu rút ra sau khi xác minh token đăng nhập hợp lệ.
    public record LoginToken(String username, long tokenVersion) {
    }

    public String issueLoginToken(String username) {
        return issueLoginToken(username, 0L);
    }

    // Ký token đăng nhập kèm tokenVersion để đổi mật khẩu hoặc đăng xuất là thu hồi được ngay.
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

    // Xác minh JWT đăng nhập; từ chối vé phát video và token thiếu hoặc sai kiểu claim ver.
    public Optional<LoginToken> parseLoginToken(String token) {
        Optional<Claims> parsed = parseVerifiedClaims(token);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        Claims claims = parsed.get();
        // Chặn tuyệt đối không cho mang vé phát video ra dùng như token đăng nhập.
        if (TYPE_PLAYBACK_GRANT.equals(claims.get(CLAIM_TYPE))
                || PURPOSE_PLAYBACK.equals(claims.get(CLAIM_PURPOSE))) {
            return Optional.empty();
        }
        String subject = claims.getSubject();
        if (subject == null || subject.isEmpty() || subject.length() > MAX_SUBJECT_LENGTH) {
            return Optional.empty();
        }
        // Claim ver phải là số nguyên không âm để loại các token cũ thiếu phiên bản.
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

    Optional<String> loginSubject(String token) {
        return parseLoginToken(token).map(LoginToken::username);
    }

    // Kết quả xác minh vé phát video, kèm userId và tokenVersion khi người xem đã đăng nhập.
    public record VerifiedPlaybackGrant(PlaybackPrincipal principal, Long userId, Long tokenVersion) {
    }

    // Cấp vé phát video ngắn hạn gắn với một assetId cụ thể (bản cũ theo username).
    public String issuePlaybackGrant(String assetId, String username, Role role) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(playbackGrantValidity);
        String subject = (username == null || username.isEmpty()) ? ANONYMOUS_SUBJECT : username;
        String roleName = role == null ? ROLE_ANONYMOUS : role.name();
        return Jwts.builder()
                .subject(subject)
                .claim(CLAIM_ASSET_ID, assetId)
                .claim(CLAIM_ROLE, roleName)
                .claim(CLAIM_TYPE, TYPE_PLAYBACK_GRANT)
                .claim(CLAIM_PURPOSE, PURPOSE_PLAYBACK)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    // Cấp vé phát video gắn userId và tokenVersion thay vì username, để đăng xuất hoặc khóa tài khoản là thu hồi được liền.
    public String issuePlaybackGrant(String assetId, Long userId, long tokenVersion, Role role) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(playbackGrantValidity);
        String subject = userId == null ? ANONYMOUS_SUBJECT : String.valueOf(userId);
        String roleName = role == null ? ROLE_ANONYMOUS : role.name();
        var builder = Jwts.builder()
                .subject(subject)
                .claim(CLAIM_ASSET_ID, assetId)
                .claim(CLAIM_ROLE, roleName)
                .claim(CLAIM_TYPE, TYPE_PLAYBACK_GRANT)
                .claim(CLAIM_PURPOSE, PURPOSE_PLAYBACK)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt));
        if (userId != null) {
            builder.claim(CLAIM_USER_ID, userId).claim(CLAIM_VERSION, tokenVersion);
        }
        return builder.signWith(signingKey, Jwts.SIG.HS256).compact();
    }

    public Optional<PlaybackPrincipal> playbackGrantPrincipal(String token, String assetId) {
        return parsePlaybackGrant(token, assetId).map(VerifiedPlaybackGrant::principal);
    }

    // Kiểm tra vé phát video có đúng loại và khớp assetId hay không, rồi ánh xạ sang PlaybackPrincipal.
    public Optional<VerifiedPlaybackGrant> parsePlaybackGrant(String token, String assetId) {
        if (assetId == null || assetId.isEmpty()) {
            return Optional.empty();
        }
        Optional<Claims> parsed = parseVerifiedClaims(token);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        Claims claims = parsed.get();
        // Vé phải mang đúng loại playback_grant và đúng mã assetId đang yêu cầu.
        if (!TYPE_PLAYBACK_GRANT.equals(claims.get(CLAIM_TYPE))
                || !assetId.equals(claims.get(CLAIM_ASSET_ID))) {
            return Optional.empty();
        }
        Long userId = readNonNegativeLong(claims.get(CLAIM_USER_ID));
        Long version = readNonNegativeLong(claims.get(CLAIM_VERSION));
        // Đổi claim role sang nhánh tương ứng của PlaybackPrincipal.
        Object rawRole = claims.get(CLAIM_ROLE);
        String roleName = rawRole == null ? "" : rawRole.toString();
        if (Role.ROLE_ADMIN.name().equals(roleName)) {
            return Optional.of(new VerifiedPlaybackGrant(new PlaybackPrincipal.Authenticated(Role.ROLE_ADMIN), userId, version));
        }
        if (Role.ROLE_USER.name().equals(roleName)) {
            return Optional.of(new VerifiedPlaybackGrant(new PlaybackPrincipal.Authenticated(Role.ROLE_USER), userId, version));
        }
        return Optional.of(new VerifiedPlaybackGrant(PlaybackPrincipal.Anonymous.INSTANCE, null, null));
    }

    private static Long readNonNegativeLong(Object raw) {
        if (raw instanceof Integer || raw instanceof Long) {
            long number = ((Number) raw).longValue();
            return number >= 0 ? number : null;
        }
        return null;
    }

    // Kiểm tra chữ ký HS256, hạn dùng và các trường bắt buộc trước khi đọc claim.
    private Optional<Claims> parseVerifiedClaims(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jws<Claims> jws = jwtParser.parseSignedClaims(token);
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

    // Đọc khóa ký HS256 từ APP_JWT_SECRET. Việt nhớ là chỉ profile demo/test thuần mới được sinh khóa tạm,
    // hễ có mysql hay prod là bắt buộc cấu hình khóa riêng.
    private static SecretKey resolveSigningKey(String configuredSecret, Environment environment) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("(demo | test) & !mysql & !prod"))) {
                byte[] generated = new byte[MIN_SECRET_BYTES];
                new SecureRandom().nextBytes(generated);
                log.warn("APP_JWT_SECRET đang trống, tự tạo khóa ký tạm cho profile demo/test.");
                return Keys.hmacShaKeyFor(generated);
            }
            throw new IllegalStateException(
                    "APP_JWT_SECRET phải là khóa riêng dài ít nhất 32 byte, không dùng lại khóa mặc định cũ."
            );
        }
        // Giải mã Base64 rồi chặn các khóa quá ngắn dưới 32 byte hoặc khóa mẫu cũ từng nằm trong repo.
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configuredSecret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET phải là chuỗi Base64 hợp lệ, dài ít nhất 32 byte.",
                    ex
            );
        }
        if (decoded.length < MIN_SECRET_BYTES
                || Arrays.equals(decoded, LEGACY_HEX_SECRET)
                || Arrays.equals(decoded, LEGACY_TEXT_SECRET)) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET phải là khóa riêng dài ít nhất 32 byte, không dùng lại khóa mặc định cũ."
            );
        }
        return Keys.hmacShaKeyFor(decoded);
    }
}
