package com.dippie.service.profile;

import com.dippie.security.JwtService;

import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// mã hóa AES-CTR rồi ký HMAC-SHA256 bằng khóa dẫn xuất từ secret của app để client không soi được đáp án
@Component
public class MathChallengeTokenCodec {

    public static final Duration TOKEN_TTL = Duration.ofMinutes(2);

    private static final byte VERSION = 1;
    private static final int TOKEN_ID_BYTES = 16;
    private static final int IV_BYTES = 16;
    private static final int PLAINTEXT_BYTES = TOKEN_ID_BYTES + Long.BYTES + Integer.BYTES + Long.BYTES + Integer.BYTES;
    private static final int MAC_BYTES = 32;
    private static final int TOTAL_BYTES = 1 + IV_BYTES + PLAINTEXT_BYTES + MAC_BYTES;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec macKey;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public MathChallengeTokenCodec(JwtService jwtService, Clock clock) {
        this.encryptionKey = new SecretKeySpec(jwtService.derivePurposeKey("dippie-math-challenge-enc-v1"), "AES");
        this.macKey = new SecretKeySpec(jwtService.derivePurposeKey("dippie-math-challenge-mac-v1"), "HmacSHA256");
        this.clock = clock;
    }

    // đóng gói tokenId, profileId, đáp án, hạn 2 phút và cấp độ thành chuỗi mờ ký HMAC
    public IssuedToken issue(long profileId, int answer, int level) {
        Instant expiresAt = clock.instant().plus(TOKEN_TTL);
        byte[] tokenIdRaw = new byte[TOKEN_ID_BYTES];
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(tokenIdRaw);
        random.nextBytes(iv);

        byte[] plaintext = serializePayload(tokenIdRaw, profileId, answer, expiresAt.getEpochSecond(), level);
        byte[] ciphertext = crypt(Cipher.ENCRYPT_MODE, iv, plaintext);
        byte[] body = new byte[1 + IV_BYTES + PLAINTEXT_BYTES];
        body[0] = VERSION;
        System.arraycopy(iv, 0, body, 1, IV_BYTES);
        System.arraycopy(ciphertext, 0, body, 1 + IV_BYTES, PLAINTEXT_BYTES);

        byte[] mac = computeMac(body);
        byte[] full = new byte[TOTAL_BYTES];
        System.arraycopy(body, 0, full, 0, body.length);
        System.arraycopy(mac, 0, full, body.length, MAC_BYTES);

        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(full);
        String tokenId = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenIdRaw);
        return new IssuedToken(encoded, tokenId, expiresAt);
    }

    // kiểm tra chữ ký HMAC trước rồi mới giải mã, sai chữ ký hay hỏng định dạng là trả rỗng ngay
    public Optional<DecodedToken> decode(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        byte[] full;
        try {
            full = Base64.getUrlDecoder().decode(rawToken.trim());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        if (full.length != TOTAL_BYTES || full[0] != VERSION) {
            return Optional.empty();
        }
        int bodyLength = 1 + IV_BYTES + PLAINTEXT_BYTES;
        byte[] body = Arrays.copyOfRange(full, 0, bodyLength);
        byte[] suppliedMac = Arrays.copyOfRange(full, bodyLength, TOTAL_BYTES);
        byte[] expectedMac = computeMac(body);
        if (!MessageDigest.isEqual(expectedMac, suppliedMac)) {
            return Optional.empty();
        }
        byte[] iv = Arrays.copyOfRange(body, 1, 1 + IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(body, 1 + IV_BYTES, bodyLength);
        byte[] plaintext = crypt(Cipher.DECRYPT_MODE, iv, ciphertext);
        return parsePayload(plaintext);
    }

    private byte[] serializePayload(byte[] tokenIdRaw, long profileId, int answer, long expiresAtEpochSec, int level) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream(PLAINTEXT_BYTES);
                DataOutputStream out = new DataOutputStream(buffer)) {
            out.write(tokenIdRaw);
            out.writeLong(profileId);
            out.writeInt(answer);
            out.writeLong(expiresAtEpochSec);
            out.writeInt(level);
            out.flush();
            return buffer.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Không đóng gói được token câu hỏi toán.", ex);
        }
    }

    private Optional<DecodedToken> parsePayload(byte[] plaintext) {
        if (plaintext.length != PLAINTEXT_BYTES) {
            return Optional.empty();
        }
        try (ByteArrayInputStream buffer = new ByteArrayInputStream(plaintext);
                DataInputStream in = new DataInputStream(buffer)) {
            byte[] tokenIdRaw = new byte[TOKEN_ID_BYTES];
            in.readFully(tokenIdRaw);
            long profileId = in.readLong();
            int answer = in.readInt();
            long expiresAtEpochSec = in.readLong();
            int level = in.readInt();
            String tokenId = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenIdRaw);
            return Optional.of(new DecodedToken(
                    tokenId,
                    profileId,
                    answer,
                    Instant.ofEpochSecond(expiresAtEpochSec),
                    level
            ));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private byte[] crypt(int mode, byte[] iv, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(mode, encryptionKey, new IvParameterSpec(iv));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Lỗi mã hóa token câu hỏi toán.", ex);
        }
    }

    private byte[] computeMac(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(macKey);
            return mac.doFinal(body);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Lỗi ký HMAC token câu hỏi toán.", ex);
        }
    }

    public record IssuedToken(String token, String tokenId, Instant expiresAt) {
    }

    public record DecodedToken(String tokenId, long profileId, int answer, Instant expiresAt, int level) {
    }
}
