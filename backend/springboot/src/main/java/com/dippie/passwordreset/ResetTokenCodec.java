package com.dippie.passwordreset;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

// Sinh token ngẫu nhiên 256 bit từ SecureRandom và băm SHA-256 trước khi ghi xuống DB.
@Component
public class ResetTokenCodec {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final HexFormat HEX = HexFormat.of();

    private final SecureRandom secureRandom = new SecureRandom();

    // Sinh chuỗi 32 byte ngẫu nhiên rồi mã hóa Base64 URL-safe không đệm để gắn thẳng vào query string.
    public String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Băm token gốc ra chuỗi hex 64 ký tự bằng SHA-256 để lưu và tra cứu trong DB.
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
