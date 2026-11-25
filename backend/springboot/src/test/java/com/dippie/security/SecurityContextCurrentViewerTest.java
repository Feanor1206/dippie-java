package com.dippie.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dippie.common.error.ForbiddenException;
import com.dippie.common.error.UnauthorizedException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

// Test đơn vị cho SecurityContextCurrentViewer khi chưa đăng nhập, đăng nhập user thường và đăng nhập admin.
class SecurityContextCurrentViewerTest {

    private final CurrentViewer currentViewer = new SecurityContextCurrentViewer();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void handlesAnonymousUser() {
        assertThat(currentViewer.find()).isEmpty();
        assertThatThrownBy(currentViewer::require)
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Bạn cần đăng nhập để tiếp tục.");
        assertThatThrownBy(currentViewer::requireAdmin)
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Chỉ tài khoản quản trị mới dùng được mục này.");
    }

    @Test
    void distinguishesRegularUserAndAdmin() {
        setViewer(new Viewer(1L, "alice", Role.ROLE_USER));

        assertThat(currentViewer.require().isAdmin()).isFalse();
        assertThatThrownBy(currentViewer::requireAdmin)
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Chỉ tài khoản quản trị mới dùng được mục này.");

        setViewer(new Viewer(2L, "admin", Role.ROLE_ADMIN));

        assertThat(currentViewer.requireAdmin().isAdmin()).isTrue();
    }

    private static void setViewer(Viewer viewer) {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                viewer,
                null,
                List.of(new SimpleGrantedAuthority(viewer.role().name()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
