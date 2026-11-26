package com.dippie.dto.user;

import com.dippie.model.user.User;

// hồ sơ công khai trả client, tách khỏi entity User kẻo lỡ lộ mật khẩu hay tokenVersion
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
