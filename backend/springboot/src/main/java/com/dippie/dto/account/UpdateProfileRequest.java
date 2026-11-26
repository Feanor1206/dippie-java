package com.dippie.dto.account;

// Payload cho PUT /api/users/me khi người dùng đổi họ tên hoặc email.
public record UpdateProfileRequest(String fullName, String email) {

    public String rawEmail() {
        return email == null ? "" : email;
    }
}
