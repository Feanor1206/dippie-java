package com.dippie.security;

import com.dippie.exception.ForbiddenException;
import com.dippie.exception.UnauthorizedException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

// đọc Viewer hiện tại từ SecurityContextHolder sau khi JwtAuthenticationFilter xác thực xong
@Component
public class SecurityContextCurrentViewer implements CurrentViewer {

    @Override
    public Optional<Viewer> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof Viewer viewer) {
            return Optional.of(viewer);
        }
        return Optional.empty();
    }

    @Override
    public Viewer require() {
        return find().orElseThrow(() -> new UnauthorizedException("Bạn cần đăng nhập để tiếp tục."));
    }

    @Override
    public Viewer requireAdmin() {
        Viewer viewer = find()
                .orElseThrow(() -> new ForbiddenException("Chỉ tài khoản quản trị mới dùng được mục này."));
        if (viewer.isChildProfile()) {
            throw new ForbiddenException("Hồ sơ trẻ em không thể truy cập khu vực quản trị.");
        }
        if (!viewer.isAdmin()) {
            throw new ForbiddenException("Chỉ tài khoản quản trị mới dùng được mục này.");
        }
        return viewer;
    }

    @Override
    public Viewer requireMainProfile(String forbiddenMessage) {
        Viewer viewer = require();
        if (viewer.isChildProfile()) {
            throw new ForbiddenException(forbiddenMessage);
        }
        return viewer;
    }
}
