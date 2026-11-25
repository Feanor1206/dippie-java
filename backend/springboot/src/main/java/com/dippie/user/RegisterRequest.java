package com.dippie.user;

// Dữ liệu đăng ký tài khoản mới gửi lên POST /api/auth/register, gồm thông tin tài khoản và cờ đồng ý điều khoản.
public record RegisterRequest(
        String username,
        String email,
        String password,
        String fullName,
        Boolean acceptedTerms
) {

    public RegisterRequest(String username, String email, String password, String fullName) {
        this(username, email, password, fullName, null);
    }

    public String rawUsername() {
        return username == null ? "" : username;
    }

    public String rawEmail() {
        return email == null ? "" : email;
    }

    public String rawPassword() {
        return password == null ? "" : password;
    }

    public boolean isTermsAccepted() {
        return Boolean.TRUE.equals(acceptedTerms);
    }
}
