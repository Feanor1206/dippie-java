package com.dippie.dto.account;

// body đổi mật khẩu, hai hàm raw* giữ nguyên dấu cách vì mật khẩu có thể có khoảng trắng
public record ChangePasswordRequest(String currentPassword, String newPassword) {

    public String rawCurrentPassword() {
        return currentPassword == null ? "" : currentPassword;
    }

    public String rawNewPassword() {
        return newPassword == null ? "" : newPassword;
    }
}
