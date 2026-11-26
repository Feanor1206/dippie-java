package com.dippie.dto.user;

// Dữ liệu gửi lên POST /api/auth/login; nhận cả usernameOrEmail lẫn username để tương thích với client cũ.
public record LoginRequest(String usernameOrEmail, String username, String password) {

    // Ưu tiên usernameOrEmail nếu client gửi cả hai trường.
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
