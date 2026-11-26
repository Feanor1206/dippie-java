package com.dippie.service.play;

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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// mã hóa AES-CTR kèm chữ ký HMAC-SHA256 tách riêng theo loại token (thuốc Dieppie, rương báu, ôn tập) và chống dùng lại
@Component
public class PlayTokenCodec {

    public static final byte KIND_MEDICINE_MATH = 1;
    public static final byte KIND_LOOT_DROP = 2;
    public static final byte KIND_LOOT_ANSWER = 3;
    public static final byte KIND_RECAP_QUIZ = 4;

    private static final byte VERSION = 1;
    private static final int TOKEN_ID_BYTES = 16;
    private static final int IV_BYTES = 16;
    private static final int MAC_BYTES = 32;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec macKey;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Instant> usedTokenIds = new LinkedHashMap<>();
    private final Object usedTokensLock = new Object();

    public PlayTokenCodec(JwtService jwtService, Clock clock) {
        this.encryptionKey = new SecretKeySpec(jwtService.derivePurposeKey("dippie-kid-play-enc-v1"), "AES");
        this.macKey = new SecretKeySpec(jwtService.derivePurposeKey("dippie-kid-play-mac-v1"), "HmacSHA256");
        this.clock = clock;
    }

    // đóng gói tokenId, profileId, hạn dùng và payload mật thành chuỗi mờ đã ký HMAC
    public IssuedToken issue(byte kind, long profileId, Duration ttl, String payload) {
        Instant expiresAt = clock.instant().plus(ttl);
        byte[] tokenIdRaw = new byte[TOKEN_ID_BYTES];
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(tokenIdRaw);
        random.nextBytes(iv);

        byte[] plaintext = serialize(tokenIdRaw, profileId, expiresAt.getEpochSecond(), payload);
        byte[] ciphertext = crypt(Cipher.ENCRYPT_MODE, iv, plaintext);
        byte[] body = new byte[2 + IV_BYTES + ciphertext.length];
        body[0] = VERSION;
        body[1] = kind;
        System.arraycopy(iv, 0, body, 2, IV_BYTES);
        System.arraycopy(ciphertext, 0, body, 2 + IV_BYTES, ciphertext.length);

        byte[] mac = computeMac(body);
        byte[] full = new byte[body.length + MAC_BYTES];
        System.arraycopy(body, 0, full, 0, body.length);
        System.arraycopy(mac, 0, full, body.length, MAC_BYTES);

        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(full);
        String tokenId = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenIdRaw);
        return new IssuedToken(encoded, tokenId, expiresAt);
    }

    // xác thực chữ ký HMAC và đúng loại token trước khi giải mã payload
    public Optional<DecodedToken> decode(String rawToken, byte expectedKind) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 4096) {
            return Optional.empty();
        }
        byte[] full;
        try {
            full = Base64.getUrlDecoder().decode(rawToken.trim());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        int minBytes = 2 + IV_BYTES + TOKEN_ID_BYTES + Long.BYTES + Long.BYTES + 2 + MAC_BYTES;
        if (full.length < minBytes || full[0] != VERSION || full[1] != expectedKind) {
            return Optional.empty();
        }
        int bodyLength = full.length - MAC_BYTES;
        byte[] body = Arrays.copyOfRange(full, 0, bodyLength);
        byte[] suppliedMac = Arrays.copyOfRange(full, bodyLength, full.length);
        byte[] expectedMac = computeMac(body);
        if (!MessageDigest.isEqual(expectedMac, suppliedMac)) {
            return Optional.empty();
        }
        byte[] iv = Arrays.copyOfRange(body, 2, 2 + IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(body, 2 + IV_BYTES, bodyLength);
        byte[] plaintext = crypt(Cipher.DECRYPT_MODE, iv, ciphertext);
        return parse(plaintext, expectedKind);
    }

    public boolean isUsed(String tokenId, Instant now) {
        synchronized (usedTokensLock) {
            pruneExpired(now);
            return usedTokenIds.containsKey(tokenId);
        }
    }

    // đánh dấu token đã dùng 1 lần cho tới khi hết hạn
    public boolean tryRedeem(String tokenId, Instant expiresAt, Instant now) {
        synchronized (usedTokensLock) {
            pruneExpired(now);
            if (usedTokenIds.containsKey(tokenId)) {
                return false;
            }
            usedTokenIds.put(tokenId, expiresAt);
            return true;
        }
    }

    public void resetUsedTokens() {
        synchronized (usedTokensLock) {
            usedTokenIds.clear();
        }
    }

    private void pruneExpired(Instant now) {
        Iterator<Map.Entry<String, Instant>> iterator = usedTokenIds.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().getValue().isAfter(now)) {
                iterator.remove();
            }
        }
    }

    private byte[] serialize(byte[] tokenIdRaw, long profileId, long expiresAtEpochSec, String payload) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(buffer)) {
            out.write(tokenIdRaw);
            out.writeLong(profileId);
            out.writeLong(expiresAtEpochSec);
            out.writeUTF(payload == null ? "" : payload);
            out.flush();
            return buffer.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Không đóng gói được token tính năng trẻ em.", ex);
        }
    }

    private Optional<DecodedToken> parse(byte[] plaintext, byte kind) {
        try (ByteArrayInputStream buffer = new ByteArrayInputStream(plaintext);
                DataInputStream in = new DataInputStream(buffer)) {
            byte[] tokenIdRaw = new byte[TOKEN_ID_BYTES];
            in.readFully(tokenIdRaw);
            long profileId = in.readLong();
            long expiresAtEpochSec = in.readLong();
            String payload = in.readUTF();
            if (in.available() != 0) {
                return Optional.empty();
            }
            String tokenId = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenIdRaw);
            return Optional.of(new DecodedToken(
                    kind,
                    tokenId,
                    profileId,
                    Instant.ofEpochSecond(expiresAtEpochSec),
                    payload
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
            throw new IllegalStateException("Lỗi mã hóa token tính năng trẻ em.", ex);
        }
    }

    private byte[] computeMac(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(macKey);
            return mac.doFinal(body);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Lỗi ký HMAC token tính năng trẻ em.", ex);
        }
    }

    public record IssuedToken(String token, String tokenId, Instant expiresAt) {
    }

    public record DecodedToken(byte kind, String tokenId, long profileId, Instant expiresAt, String payload) {
    }
}
