package com.dippie.account;

public record UpdateProfileRequest(String fullName, String email) {

    public String rawEmail() {
        return email == null ? "" : email;
    }
}
