package com.dippie.user;

public record AuthResponse(
        String token,
        String type,
        Long id,
        String username,
        String email,
        String fullName,
        String role
) {

    public static AuthResponse of(User user, String token) {
        return new AuthResponse(
                token,
                "Bearer",
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name()
        );
    }
}
