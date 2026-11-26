package com.dippie.dto.account;

// body nhận token với mật khẩu mới ở bước chốt đặt lại mật khẩu
public record PasswordResetConfirmRequest(String token, String newPassword) {

    public String rawToken() {
        return token == null ? "" : token;
    }

    public String rawNewPassword() {
        return newPassword == null ? "" : newPassword;
    }
}
