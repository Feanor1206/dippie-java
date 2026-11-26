package com.dippie.dto.account;

// mật khẩu xác nhận lúc user tự xóa chính tài khoản đó
public record DeleteAccountRequest(String password) {

    public String rawPassword() {
        return password == null ? "" : password;
    }
}
