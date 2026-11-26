package com.dippie.security;

import com.dippie.model.user.Role;

// Bản sao gọn của người dùng đang đăng nhập để tầng security và controller dùng mà không kéo theo JPA entity User.
public record Viewer(Long userId, String username, Role role) {

    public boolean isAdmin() {
        return role == Role.ROLE_ADMIN;
    }
}
