package com.dippie.dto.account;

// body gửi email lúc user bấm quên mật khẩu
public record PasswordResetRequestDto(String email) {

    public String rawEmail() {
        return email == null ? "" : email;
    }
}
