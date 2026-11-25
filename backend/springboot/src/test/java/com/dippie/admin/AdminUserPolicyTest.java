package com.dippie.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dippie.common.error.ConflictException;
import com.dippie.common.error.InvalidRequestException;
import com.dippie.security.Role;
import com.dippie.security.Viewer;
import com.dippie.user.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AdminUserPolicyTest {

    private final AdminUserPolicy policy = new AdminUserPolicy();

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

    @Test
    void allowsDemotingOrDisablingAdminWhenAnotherEnabledAdminRemains() {
        Viewer actor = new Viewer(1L, "admin1", Role.ROLE_ADMIN);
        User otherAdmin = createUser(2L, "admin2", Role.ROLE_ADMIN);

        assertThatCode(() -> policy.ensureCanChangeRole(actor, otherAdmin, Role.ROLE_USER, 2))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.ensureCanChangeStatus(actor, otherAdmin, false, 2))
                .doesNotThrowAnyException();
    }

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
