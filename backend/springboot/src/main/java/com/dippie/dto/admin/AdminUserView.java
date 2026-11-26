package com.dippie.dto.admin;

import com.dippie.model.user.User;

import java.time.LocalDateTime;

// thông tin gọn của một tài khoản trên trang quản lý user
public record AdminUserView(
        Long id,
        String username,
        String email,
        String fullName,
        String role,
        boolean enabled,
        LocalDateTime createdAt
) {

    // dựng từ entity User, chỉ nhặt đúng mấy trường trang admin cần
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
