package com.dippie.account;

public record ChangePasswordRequest(String currentPassword, String newPassword) {

    public String rawCurrentPassword() {
        return currentPassword == null ? "" : currentPassword;
    }

    public String rawNewPassword() {
        return newPassword == null ? "" : newPassword;
    }
}
