package com.dippie.service.admin;

import com.dippie.exception.ConflictException;
import com.dippie.exception.InvalidRequestException;
import com.dippie.model.user.Role;
import com.dippie.model.user.User;
import com.dippie.security.Viewer;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// unit test mấy luật trong AdminUserPolicy với chuyện nhảy token_version trên User
class AdminUserPolicyTest {

    private final AdminUserPolicy policy = new AdminUserPolicy();

    // trim xong nhận role hợp lệ, trống hay lạ là quăng 400
    @Test
    void parsesValidRolesAndRejectsInvalidValues() {
        assertThat(policy.parseRole("ROLE_USER")).isEqualTo(Role.ROLE_USER);
        assertThat(policy.parseRole(" ROLE_ADMIN ")).isEqualTo(Role.ROLE_ADMIN);

        assertThatThrownBy(() -> policy.parseRole(null))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> policy.parseRole("   "))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> policy.parseRole("ROLE_SUPERADMIN"))
                .isInstanceOf(InvalidRequestException.class);
    }

    // admin không được tự hạ quyền hay tự khóa chính tài khoản đó đâu nha
    @Test
    void forbidsAdminFromChangingOwnRoleOrStatus() {
        Viewer actor = new Viewer(1L, "admin1", Role.ROLE_ADMIN);
        User self = createUser(1L, "admin1", Role.ROLE_ADMIN);

        assertThatThrownBy(() -> policy.ensureCanChangeRole(actor, self, Role.ROLE_USER, 2))
                .isInstanceOf(ConflictException.class)
                .hasMessage(AdminUserPolicy.SELF_CHANGE_MESSAGE);

        assertThatThrownBy(() -> policy.ensureCanChangeStatus(actor, self, false, 2))
                .isInstanceOf(ConflictException.class)
                .hasMessage(AdminUserPolicy.SELF_CHANGE_MESSAGE);
    }

    // còn đúng 1 admin đang mở thì cấm cả hạ quyền lẫn khóa tài khoản đó
    @Test
    void forbidsDemotingOrDisablingLastEnabledAdmin() {
        Viewer actor = new Viewer(1L, "admin1", Role.ROLE_ADMIN);
        User otherAdmin = createUser(2L, "admin2", Role.ROLE_ADMIN);

        assertThatThrownBy(() -> policy.ensureCanChangeRole(actor, otherAdmin, Role.ROLE_USER, 1))
                .isInstanceOf(ConflictException.class)
                .hasMessage(AdminUserPolicy.LAST_ADMIN_MESSAGE);

        assertThatThrownBy(() -> policy.ensureCanChangeStatus(actor, otherAdmin, false, 1))
                .isInstanceOf(ConflictException.class)
                .hasMessage(AdminUserPolicy.LAST_ADMIN_MESSAGE);
    }

    // có từ 2 admin đang mở trở lên thì hạ quyền hay khóa admin kia vô tư
    @Test
    void allowsDemotingOrDisablingAdminWhenAnotherEnabledAdminRemains() {
        Viewer actor = new Viewer(1L, "admin1", Role.ROLE_ADMIN);
        User otherAdmin = createUser(2L, "admin2", Role.ROLE_ADMIN);

        assertThatCode(() -> policy.ensureCanChangeRole(actor, otherAdmin, Role.ROLE_USER, 2))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.ensureCanChangeStatus(actor, otherAdmin, false, 2))
                .doesNotThrowAnyException();
    }

    // sửa user thường hay admin đã khóa sẵn thì số admin sống không giảm nên vẫn cho qua
    @Test
    void allowsModifyingDisabledAdminOrRegularUserWhenOnlyOneEnabledAdminExists() {
        Viewer actor = new Viewer(1L, "admin1", Role.ROLE_ADMIN);
        User regularUser = createUser(2L, "viewer", Role.ROLE_USER);
        User disabledAdmin = createUser(3L, "disabledAdmin", Role.ROLE_ADMIN);
        disabledAdmin.disable();

        assertThatCode(() -> policy.ensureCanChangeRole(actor, regularUser, Role.ROLE_ADMIN, 1))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.ensureCanChangeStatus(actor, regularUser, false, 1))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.ensureCanChangeRole(actor, disabledAdmin, Role.ROLE_USER, 1))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.ensureCanChangeStatus(actor, disabledAdmin, true, 1))
                .doesNotThrowAnyException();
    }

    // đổi mật khẩu, khóa mở hay đổi quyền là tăng token_version đá phiên cũ, riêng sửa hồ sơ thì giữ nguyên
    @Test
    void userLifecycleMutationsIncrementTokenVersionWhileProfileUpdateDoesNot() {
        User user = createUser(10L, "alice", Role.ROLE_USER);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getTokenVersion()).isZero();

        user.updateProfile("Alice New", "alice.new@example.com");
        assertThat(user.getFullName()).isEqualTo("Alice New");
        assertThat(user.getEmail()).isEqualTo("alice.new@example.com");
        assertThat(user.getTokenVersion()).isZero();

        user.changePassword("encoded-2");
        assertThat(user.getTokenVersion()).isEqualTo(1L);

        user.disable();
        assertThat(user.isEnabled()).isFalse();
        assertThat(user.getTokenVersion()).isEqualTo(2L);

        user.enable();
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getTokenVersion()).isEqualTo(3L);

        user.changeRole(Role.ROLE_ADMIN);
        assertThat(user.getRole()).isEqualTo(Role.ROLE_ADMIN);
        assertThat(user.getTokenVersion()).isEqualTo(4L);
    }

    private static User createUser(Long id, String username, Role role) {
        User user = new User(username, username + "@example.com", "encoded", username, role, LocalDateTime.of(2026, 1, 1, 0, 0));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
