package com.dippie.dto.user;

// body POST /api/auth/login, nhận cả usernameOrEmail lẫn username cho khỏi gãy client cũ
public record LoginRequest(String usernameOrEmail, String username, String password) {

    // client gửi cả hai thì ưu tiên lấy usernameOrEmail
    public String rawIdentifier() {
        if (usernameOrEmail != null && !usernameOrEmail.isEmpty()) {
            return usernameOrEmail;
        }
        return username == null ? "" : username;
    }

    public String rawPassword() {
        return password == null ? "" : password;
    }
}
