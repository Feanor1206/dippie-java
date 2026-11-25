package com.dippie.admin;

import com.dippie.common.error.ConflictException;
import com.dippie.common.error.InvalidRequestException;
import com.dippie.security.Role;
import com.dippie.security.Viewer;
import com.dippie.user.User;
import java.util.Objects;
import org.springframework.stereotype.Component;

// Tách riêng các luật chặn khi quản trị tài khoản khỏi service để dễ đọc và viết unit test độc lập.
@Component
class AdminUserPolicy {

    static final String SELF_CHANGE_MESSAGE = "Bạn không thể tự đổi quyền hoặc khóa tài khoản của chính mình.";
    static final String LAST_ADMIN_MESSAGE = "Hệ thống cần giữ lại ít nhất một quản trị viên đang hoạt động.";
    private static final String INVALID_ROLE_MESSAGE = "Vai trò không hợp lệ.";

    // Đọc chuỗi vai trò từ request sang enum Role, sai hoặc bỏ trống thì báo lỗi 400.
    Role parseRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            throw new InvalidRequestException(INVALID_ROLE_MESSAGE);
        }
        try {
            return Role.valueOf(rawRole.trim());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(INVALID_ROLE_MESSAGE);
        }
    }

    // Chặn tự đổi quyền chính mình và chặn hạ quyền admin đang hoạt động cuối cùng.
    public void ensureCanChangeRole(Viewer actor, User target, Role newRole, long enabledAdminCount) {
        ensureNotSelf(actor, target);
        if (target.isAdmin() && target.isEnabled() && newRole != Role.ROLE_ADMIN && enabledAdminCount <= 1) {
            throw new ConflictException(LAST_ADMIN_MESSAGE);
        }
    }

    // Chặn tự khóa tài khoản chính mình và chặn khóa admin đang hoạt động cuối cùng.
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
