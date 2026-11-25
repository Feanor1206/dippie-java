package com.dippie.security;

import com.dippie.common.error.ForbiddenException;
import com.dippie.common.error.UnauthorizedException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

// Đọc Viewer hiện tại từ SecurityContextHolder sau khi JwtAuthenticationFilter đã xác thực xong.
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
        return find()
                .filter(Viewer::isAdmin)
                .orElseThrow(() -> new ForbiddenException("Chỉ tài khoản quản trị mới dùng được mục này."));
    }
}
