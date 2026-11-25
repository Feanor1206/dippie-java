package com.dippie.passwordreset;

// Thân request chứa email người dùng nhập khi bấm quên mật khẩu.
public record PasswordResetRequestDto(String email) {

    public String rawEmail() {
        return email == null ? "" : email;
    }
}
