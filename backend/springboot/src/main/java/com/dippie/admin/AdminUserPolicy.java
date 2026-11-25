package com.dippie.admin;

import com.dippie.common.error.ConflictException;
import com.dippie.common.error.InvalidRequestException;
import com.dippie.security.Role;
import com.dippie.security.Viewer;
import com.dippie.user.User;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class AdminUserPolicy {

    public static final String SELF_CHANGE_MESSAGE = "Không thể tự thay đổi quyền hoặc trạng thái của chính mình.";
    public static final String LAST_ADMIN_MESSAGE = "Hệ thống phải còn ít nhất một quản trị viên đang hoạt động.";
    public static final String INVALID_ROLE_MESSAGE = "Vai trò không hợp lệ.";

    public Role parseRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            throw new InvalidRequestException(INVALID_ROLE_MESSAGE);
        }
        try {
            return Role.valueOf(rawRole.trim());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(INVALID_ROLE_MESSAGE);
        }
    }

    public void ensureCanChangeRole(Viewer actor, User target, Role newRole, long enabledAdminCount) {
        ensureNotSelf(actor, target);
        if (target.isAdmin() && target.isEnabled() && newRole != Role.ROLE_ADMIN && enabledAdminCount <= 1) {
            throw new ConflictException(LAST_ADMIN_MESSAGE);
        }
    }

    public void ensureCanChangeStatus(Viewer actor, User target, boolean targetEnabled, long enabledAdminCount) {
        ensureNotSelf(actor, target);
        if (target.isAdmin() && target.isEnabled() && !targetEnabled && enabledAdminCount <= 1) {
            throw new ConflictException(LAST_ADMIN_MESSAGE);
        }
    }

    private void ensureNotSelf(Viewer actor, User target) {
        if (actor != null && Objects.equals(actor.userId(), target.getId())) {
            throw new ConflictException(SELF_CHANGE_MESSAGE);
        }
    }
}
