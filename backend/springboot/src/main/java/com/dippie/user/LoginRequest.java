package com.dippie.user;

public record LoginRequest(String usernameOrEmail, String username, String password) {

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
