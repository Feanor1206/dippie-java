package com.dippie.security;

import com.dippie.common.error.InvalidRequestException;
import java.nio.charset.StandardCharsets;

public record PasswordPolicy(int minRegisterLength, int minAdminLength, int maxUtf8Bytes) {

    public static final PasswordPolicy DEFAULT = new PasswordPolicy(6, 16, 72);

    public boolean exceedsMaxBytes(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length > maxUtf8Bytes;
    }

    public void validateForRegistration(String password) {
        if (password == null || password.length() < minRegisterLength) {
            throw new InvalidRequestException("Mật khẩu phải có ít nhất 6 ký tự");
        }
        if (exceedsMaxBytes(password)) {
            throw new InvalidRequestException("Mật khẩu được dùng tối đa 72 byte UTF-8.");
        }
    }

    public void validateForAdminBootstrap(String password) {
        if (password == null || password.trim().length() < minAdminLength || exceedsMaxBytes(password)) {
            throw new IllegalStateException("Mật khẩu quản trị phải dài từ 16 ký tự đến 72 byte.");
        }
    }
}
