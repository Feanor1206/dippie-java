package com.dippie.user;

// Phản hồi sau khi đăng nhập, đăng ký hoặc đổi mật khẩu: chứa JWT dạng Bearer và thông tin cơ bản của tài khoản.
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
