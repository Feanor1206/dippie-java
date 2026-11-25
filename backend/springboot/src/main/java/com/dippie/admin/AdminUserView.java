package com.dippie.admin;

import com.dippie.user.User;
import java.time.LocalDateTime;

public record AdminUserView(
        Long id,
        String username,
        String email,
        String fullName,
        String role,
        boolean enabled,
        LocalDateTime createdAt
) {

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
