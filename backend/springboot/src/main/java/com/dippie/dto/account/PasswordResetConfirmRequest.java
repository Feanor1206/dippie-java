package com.dippie.dto.account;

// Payload nhận token và mật khẩu mới ở bước xác nhận đặt lại mật khẩu.
public record PasswordResetConfirmRequest(String token, String newPassword) {

    public String rawToken() {
        return token == null ? "" : token;
    }

    public String rawNewPassword() {
        return newPassword == null ? "" : newPassword;
    }
}
