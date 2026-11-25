package com.dippie.security;

public record Viewer(Long userId, String username, Role role) {

    public boolean isAdmin() {
        return role == Role.ROLE_ADMIN;
    }
}
