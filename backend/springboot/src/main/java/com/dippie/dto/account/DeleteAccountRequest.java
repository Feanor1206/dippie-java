package com.dippie.dto.account;

// Mật khẩu xác nhận gửi kèm khi người dùng yêu cầu xóa tài khoản của chính mình.
public record DeleteAccountRequest(String password) {

    public String rawPassword() {
        return password == null ? "" : password;
    }
}
