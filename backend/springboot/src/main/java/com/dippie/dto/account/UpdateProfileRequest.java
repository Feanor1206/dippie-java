package com.dippie.dto.account;

// body cho PUT /api/users/me khi user đổi họ tên hoặc email
public record UpdateProfileRequest(String fullName, String email) {

    public String rawEmail() {
        return email == null ? "" : email;
    }
}
