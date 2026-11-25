package com.dippie.user;

public record UserProfileResponse(
        Long id,
        String username,
        String email,
        String fullName,
        String role
) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name()
        );
    }
}
