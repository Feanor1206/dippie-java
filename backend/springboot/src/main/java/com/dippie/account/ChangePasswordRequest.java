package com.dippie.account;

// Payload đổi mật khẩu; hai hàm raw* giữ nguyên khoảng trắng vì mật khẩu có thể chứa dấu cách.
public record ChangePasswordRequest(String currentPassword, String newPassword) {

    public String rawCurrentPassword() {
        return currentPassword == null ? "" : currentPassword;
    }

    public String rawNewPassword() {
        return newPassword == null ? "" : newPassword;
    }
}
