package com.dippie.service.account;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// sinh token ngẫu nhiên 256 bit từ SecureRandom rồi băm SHA-256 trước khi lưu DB
@Component
public class ResetTokenCodec {

    private static final HexFormat HEX = HexFormat.of();

    private final SecureRandom secureRandom = new SecureRandom();

    // lấy 32 byte ngẫu nhiên rồi mã hóa Base64 URL-safe không đệm để gắn thẳng lên query string
    public String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // băm token gốc ra hex 64 ký tự bằng SHA-256 để lưu với tra trong DB
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Môi trường hiện tại không hỗ trợ thuật toán băm SHA-256.", ex);
        }
    }
}
