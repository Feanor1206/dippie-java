package com.dippie.dto.admin;

import com.dippie.model.user.User;

import java.time.LocalDateTime;

// Dữ liệu tóm tắt một tài khoản hiển thị trên trang quản lý người dùng.
public record AdminUserView(
        Long id,
        String username,
        String email,
        String fullName,
        String role,
        boolean enabled,
        LocalDateTime createdAt
) {

    // Dựng dữ liệu phản hồi từ entity User, chỉ lấy các trường cần đưa ra giao diện admin.
    public static AdminUserView from(User user) {
        return new AdminUserView(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }
}
