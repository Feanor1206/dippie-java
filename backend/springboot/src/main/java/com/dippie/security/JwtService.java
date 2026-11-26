package com.dippie.security;

import com.dippie.model.user.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

// ký và verify JWT (HS256) cho cả phiên đăng nhập lẫn vé phát video ngắn hạn
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int MIN_SECRET_BYTES = 32;
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_PURPOSE = "purpose";
    private static final String CLAIM_ASSET_ID = "assetId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_VERSION = "ver";
    private static final String CLAIM_PROFILE_ID = "pid";
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

    // dữ liệu lấy ra sau khi verify token đăng nhập hợp lệ, profileId null là hồ sơ chính
    public record LoginToken(String username, long tokenVersion, Long profileId) {

        public LoginToken(String username, long tokenVersion) {
            this(username, tokenVersion, null);
        }
    }

    public String issueLoginToken(String username) {
        return issueLoginToken(username, 0L, null);
    }

    // ký token đăng nhập kèm tokenVersion để đổi mật khẩu hay đăng xuất là thu hồi được liền
    public String issueLoginToken(String username, long tokenVersion) {
        return issueLoginToken(username, tokenVersion, null);
    }

    // ký token đăng nhập có thêm claim pid khi đang bật hồ sơ trẻ em, còn hồ sơ chính thì bỏ hẳn claim này
    public String issueLoginToken(String username, long tokenVersion, Long profileId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(expiration);
        var builder = Jwts.builder()
                .subject(username)
                .claim(CLAIM_VERSION, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt));
        if (profileId != null) {
            builder.claim(CLAIM_PROFILE_ID, profileId);
        }
        return builder.signWith(signingKey, Jwts.SIG.HS256).compact();
    }

    // verify JWT đăng nhập, loại luôn vé phát video và mấy token thiếu hoặc sai kiểu claim ver hay pid
    public Optional<LoginToken> parseLoginToken(String token) {
        Optional<Claims> parsed = parseVerifiedClaims(token);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        Claims claims = parsed.get();
        // chặn cứng không cho lấy vé phát video giả làm token đăng nhập
        if (TYPE_PLAYBACK_GRANT.equals(claims.get(CLAIM_TYPE))
                || PURPOSE_PLAYBACK.equals(claims.get(CLAIM_PURPOSE))) {
            return Optional.empty();
        }
        String subject = claims.getSubject();
        if (subject == null || subject.isEmpty() || subject.length() > 50) {
            return Optional.empty();
        }
        // claim ver phải là số nguyên không âm để loại mấy token cũ thiếu phiên bản
        Object rawVersion = claims.get(CLAIM_VERSION);
        if (!(rawVersion instanceof Integer || rawVersion instanceof Long)) {
            return Optional.empty();
        }
        long version = ((Number) rawVersion).longValue();
        if (version < 0) {
            return Optional.empty();
        }
        // claim pid nếu có thì phải là số nguyên dương, sai kiểu hay <= 0 thì coi như token lỗi
        Long profileId = null;
        if (claims.containsKey(CLAIM_PROFILE_ID)) {
            Object rawProfileId = claims.get(CLAIM_PROFILE_ID);
            if (rawProfileId != null) {
                if (!(rawProfileId instanceof Integer || rawProfileId instanceof Long)) {
                    return Optional.empty();
                }
                long parsedProfileId = ((Number) rawProfileId).longValue();
                if (parsedProfileId <= 0) {
                    return Optional.empty();
                }
                profileId = parsedProfileId;
            }
        }
        return Optional.of(new LoginToken(subject, version, profileId));
    }

    Optional<String> loginSubject(String token) {
        return parseLoginToken(token).map(LoginToken::username);
    }

    // kết quả verify vé phát video, kèm userId, tokenVersion và profileId nếu người xem đang ở hồ sơ trẻ em
    public record VerifiedPlaybackGrant(
            PlaybackPrincipal principal,
            Long userId,
            Long tokenVersion,
            Long profileId
    ) {

        public VerifiedPlaybackGrant(PlaybackPrincipal principal, Long userId, Long tokenVersion) {
            this(principal, userId, tokenVersion, null);
        }
    }

    // dẫn xuất khóa con 32 byte bằng HMAC-SHA256 từ khóa bí mật hiện có của app cho từng mục đích riêng
    public byte[] derivePurposeKey(String purpose) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            return mac.doFinal(purpose.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Không khởi tạo được khóa HMAC cho " + purpose, ex);
        }
    }

    // cấp vé phát video ngắn hạn gắn với 1 assetId cụ thể (bản cũ theo username)
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

    // cấp vé phát video gắn userId và tokenVersion thay vì username để đăng xuất hay khóa nick là thu hồi được liền
    public String issuePlaybackGrant(String assetId, Long userId, long tokenVersion, Role role) {
        return issuePlaybackGrant(assetId, userId, tokenVersion, role, null);
    }

    // cấp vé phát video có kèm thêm profileId nếu đang ở hồ sơ trẻ em để stream kiểm tra khóa giờ xem
    public String issuePlaybackGrant(String assetId, Long userId, long tokenVersion, Role role, Long profileId) {
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
            if (profileId != null) {
                builder.claim(CLAIM_PROFILE_ID, profileId);
            }
        }
        return builder.signWith(signingKey, Jwts.SIG.HS256).compact();
    }

    public Optional<PlaybackPrincipal> playbackGrantPrincipal(String token, String assetId) {
        return parsePlaybackGrant(token, assetId).map(VerifiedPlaybackGrant::principal);
    }

    // check vé phát video có đúng loại và khớp assetId không rồi đổi sang PlaybackPrincipal
    public Optional<VerifiedPlaybackGrant> parsePlaybackGrant(String token, String assetId) {
        if (assetId == null || assetId.isEmpty()) {
            return Optional.empty();
        }
        Optional<Claims> parsed = parseVerifiedClaims(token);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        Claims claims = parsed.get();
        // vé phải đúng loại playback_grant và đúng assetId đang gọi
        if (!TYPE_PLAYBACK_GRANT.equals(claims.get(CLAIM_TYPE))
                || !assetId.equals(claims.get(CLAIM_ASSET_ID))) {
            return Optional.empty();
        }
        Long userId = readNonNegativeLong(claims.get(CLAIM_USER_ID));
        Long version = readNonNegativeLong(claims.get(CLAIM_VERSION));
        Long profileId = null;
        if (claims.containsKey(CLAIM_PROFILE_ID) && claims.get(CLAIM_PROFILE_ID) != null) {
            Long candidateProfileId = readNonNegativeLong(claims.get(CLAIM_PROFILE_ID));
            if (candidateProfileId == null || candidateProfileId <= 0) {
                return Optional.empty();
            }
            profileId = candidateProfileId;
        }
        // đổi claim role sang nhánh tương ứng của PlaybackPrincipal
        Object rawRole = claims.get(CLAIM_ROLE);
        String roleName = rawRole == null ? "" : rawRole.toString();
        if (Role.ROLE_ADMIN.name().equals(roleName)) {
            return Optional.of(new VerifiedPlaybackGrant(
                    new PlaybackPrincipal.Authenticated(Role.ROLE_ADMIN), userId, version, profileId));
        }
        if (Role.ROLE_USER.name().equals(roleName)) {
            return Optional.of(new VerifiedPlaybackGrant(
                    new PlaybackPrincipal.Authenticated(Role.ROLE_USER), userId, version, profileId));
        }
        return Optional.of(new VerifiedPlaybackGrant(PlaybackPrincipal.Anonymous.INSTANCE, null, null, null));
    }

    private static Long readNonNegativeLong(Object raw) {
        if (raw instanceof Integer || raw instanceof Long) {
            long number = ((Number) raw).longValue();
            return number >= 0 ? number : null;
        }
        return null;
    }

    // check chữ ký HS256, hạn dùng và mấy trường bắt buộc trước khi đọc claim
    private Optional<Claims> parseVerifiedClaims(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jws<Claims> jws = jwtParser.parseSignedClaims(token);
            if (!"HS256".equals(jws.getHeader().getAlgorithm())) {
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

    // đọc khóa ký HS256 từ APP_JWT_SECRET, Việt nhớ là chỉ profile demo/test thuần mới được sinh khóa tạm nha
    // hễ có mysql hay prod là bắt buộc cấu hình khóa riêng
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
        // giải mã Base64 rồi chặn mấy khóa dưới 32 byte hoặc khóa mẫu cũ từng nằm trong repo
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
