package com.dippie.dto.user;

import com.dippie.model.user.User;

// trả về sau đăng nhập, đăng ký hay đổi mật khẩu, gồm JWT dạng Bearer với thông tin cơ bản
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
