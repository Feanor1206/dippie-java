package com.dippie.security;

import com.dippie.model.user.Role;

// bản gọn của user đang đăng nhập cho tầng security và controller dùng mà khỏi kéo theo JPA entity User
public record Viewer(Long userId, String username, Role role) {

    public boolean isAdmin() {
        return role == Role.ROLE_ADMIN;
    }
}
