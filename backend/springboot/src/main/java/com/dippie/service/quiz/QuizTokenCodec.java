package com.dippie.service.quiz;

import com.dippie.model.quiz.QuizChallengeContext;
import com.dippie.model.quiz.QuizGrade;
import com.dippie.model.quiz.QuizOptionKey;
import com.dippie.model.quiz.QuizSubject;
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

// mã hóa AES-CTR kèm chữ ký HMAC-SHA256 cho câu hỏi trắc nghiệm để đáp án không lộ ra client và chỉ dùng đúng 1 lần
@Component
public class QuizTokenCodec {

    public static final Duration STANDARD_TTL = Duration.ofMinutes(2);

    private static final byte VERSION = 1;
    private static final int TOKEN_ID_BYTES = 16;
    private static final int IV_BYTES = 16;
    private static final int MAC_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 8192;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec macKey;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Instant> usedTokenIds = new LinkedHashMap<>();
    private final Object usedTokensLock = new Object();

    public QuizTokenCodec(JwtService jwtService, Clock clock) {
        this.encryptionKey = new SecretKeySpec(jwtService.derivePurposeKey("dippie-quiz-token-enc-v1"), "AES");
        this.macKey = new SecretKeySpec(jwtService.derivePurposeKey("dippie-quiz-token-mac-v1"), "HmacSHA256");
        this.clock = clock;
    }

    // đóng gói đáp án đã trộn, thời điểm phát và ngữ cảnh vào token mờ có chữ ký HMAC
    public IssuedToken issue(
            QuizChallengeContext context,
            long profileId,
            String scope,
            Duration ttl,
            TokenData data
    ) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);
        byte[] tokenIdRaw = new byte[TOKEN_ID_BYTES];
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(tokenIdRaw);
        random.nextBytes(iv);

        byte[] plaintext = serialize(
                tokenIdRaw,
                context,
                profileId,
                scope == null ? "" : scope,
                issuedAt.toEpochMilli(),
                expiresAt.getEpochSecond(),
                data
        );
        byte[] ciphertext = crypt(Cipher.ENCRYPT_MODE, iv, plaintext);
        byte[] body = new byte[2 + IV_BYTES + ciphertext.length];
        body[0] = VERSION;
        body[1] = (byte) context.ordinal();
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

    // kiểm tra chữ ký HMAC và đúng loại ngữ cảnh rồi mới giải mã, sai bất kỳ byte nào là từ chối ngay
    public Optional<DecodedToken> decode(String rawToken, QuizChallengeContext expectedContext) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_TOKEN_LENGTH || expectedContext == null) {
            return Optional.empty();
        }
        byte[] full;
        try {
            full = Base64.getUrlDecoder().decode(rawToken.trim());
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        int minBytes = 2 + IV_BYTES + TOKEN_ID_BYTES + Long.BYTES * 5 + MAC_BYTES;
        if (full.length < minBytes || full[0] != VERSION || full[1] != (byte) expectedContext.ordinal()) {
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
        return parse(plaintext, expectedContext);
    }

    public boolean isUsed(String tokenId, Instant now) {
        synchronized (usedTokensLock) {
            pruneExpired(now);
            return usedTokenIds.containsKey(tokenId);
        }
    }

    // khóa token dùng 1 lần cho tới khi hết hạn, tránh gửi lại cùng token sau khi đã biết đáp án
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

    // trả lại token khi giao dịch lưu dữ liệu bị lỗi để bé gửi lại không mất lượt
    public void release(String tokenId) {
        if (tokenId == null) {
            return;
        }
        synchronized (usedTokensLock) {
            usedTokenIds.remove(tokenId);
        }
    }

    public void resetUsedTokens() {
        synchronized (usedTokensLock) {
            usedTokenIds.clear();
        }
    }

    public int trackedUsedTokenCount() {
        synchronized (usedTokensLock) {
            pruneExpired(clock.instant());
            return usedTokenIds.size();
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

    private byte[] serialize(
            byte[] tokenIdRaw,
            QuizChallengeContext context,
            long profileId,
            String scope,
            long issuedAtEpochMs,
            long expiresAtEpochSec,
            TokenData data
    ) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(buffer)) {
            out.write(tokenIdRaw);
            out.writeByte(context.ordinal());
            out.writeLong(profileId);
            out.writeUTF(scope);
            out.writeLong(issuedAtEpochMs);
            out.writeLong(expiresAtEpochSec);
            out.writeLong(data.setId() == null ? -1L : data.setId());
            out.writeLong(data.questionId() == null ? -1L : data.questionId());
            out.writeByte(data.correctOption().ordinal());
            out.writeByte(data.grade().ordinal());
            out.writeByte(data.subject().ordinal());
            out.writeUTF(safeStr(data.questionKey()));
            out.writeUTF(safeStr(data.questionText()));
            out.writeUTF(safeStr(data.setName()));
            out.writeUTF(safeStr(data.correctText()));
            out.writeUTF(safeStr(data.explanation()));
            out.flush();
            return buffer.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Không đóng gói được token câu hỏi.", ex);
        }
    }

    private Optional<DecodedToken> parse(byte[] plaintext, QuizChallengeContext expectedContext) {
        try (ByteArrayInputStream buffer = new ByteArrayInputStream(plaintext);
                DataInputStream in = new DataInputStream(buffer)) {
            byte[] tokenIdRaw = new byte[TOKEN_ID_BYTES];
            in.readFully(tokenIdRaw);
            int contextOrd = in.readUnsignedByte();
            if (contextOrd != expectedContext.ordinal()) {
                return Optional.empty();
            }
            long profileId = in.readLong();
            String scope = in.readUTF();
            long issuedAtEpochMs = in.readLong();
            long expiresAtEpochSec = in.readLong();
            long rawSetId = in.readLong();
            long rawQuestionId = in.readLong();
            int correctOrd = in.readUnsignedByte();
            int gradeOrd = in.readUnsignedByte();
            int subjectOrd = in.readUnsignedByte();
            String questionKey = in.readUTF();
            String questionText = in.readUTF();
            String setName = in.readUTF();
            String correctText = in.readUTF();
            String rawExplanation = in.readUTF();
            if (in.available() != 0) {
                return Optional.empty();
            }
            QuizOptionKey[] optionKeys = QuizOptionKey.values();
            QuizGrade[] grades = QuizGrade.values();
            QuizSubject[] subjects = QuizSubject.values();
            if (correctOrd >= optionKeys.length || gradeOrd >= grades.length || subjectOrd >= subjects.length) {
                return Optional.empty();
            }
            String tokenId = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenIdRaw);
            return Optional.of(new DecodedToken(
                    tokenId,
                    expectedContext,
                    profileId,
                    scope,
                    issuedAtEpochMs,
                    Instant.ofEpochSecond(expiresAtEpochSec),
                    rawSetId > 0 ? rawSetId : null,
                    rawQuestionId > 0 ? rawQuestionId : null,
                    optionKeys[correctOrd],
                    grades[gradeOrd],
                    subjects[subjectOrd],
                    questionKey,
                    questionText,
                    setName,
                    correctText,
                    rawExplanation.isEmpty() ? null : rawExplanation
            ));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private static String safeStr(String value) {
        return value == null ? "" : value;
    }

    private byte[] crypt(int mode, byte[] iv, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(mode, encryptionKey, new IvParameterSpec(iv));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Lỗi mã hóa token câu hỏi.", ex);
        }
    }

    private byte[] computeMac(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(macKey);
            return mac.doFinal(body);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Lỗi ký HMAC token câu hỏi.", ex);
        }
    }

    public record TokenData(
            Long setId,
            Long questionId,
            QuizOptionKey correctOption,
            QuizGrade grade,
            QuizSubject subject,
            String questionKey,
            String questionText,
            String setName,
            String correctText,
            String explanation
    ) {
    }

    public record IssuedToken(String token, String tokenId, Instant expiresAt) {
    }

    public record DecodedToken(
            String tokenId,
            QuizChallengeContext context,
            long profileId,
            String scope,
            long issuedAtEpochMs,
            Instant expiresAt,
            Long setId,
            Long questionId,
            QuizOptionKey correctOption,
            QuizGrade grade,
            QuizSubject subject,
            String questionKey,
            String questionText,
            String setName,
            String correctText,
            String explanation
    ) {
    }
}
