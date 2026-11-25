package com.dippie.user;

public record RegisterRequest(String username, String email, String password, String fullName) {

    public String rawUsername() {
        return username == null ? "" : username;
    }

    public String rawEmail() {
        return email == null ? "" : email;
    }

    public String rawPassword() {
        return password == null ? "" : password;
    }
}
