package com.dippie.user;

// Hồ sơ công khai trả về cho client, tách riêng khỏi entity User để không bao giờ lộ mật khẩu hay tokenVersion.
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
