package com.dippie.service.admin;

import com.dippie.exception.ConflictException;
import com.dippie.exception.InvalidRequestException;
import com.dippie.model.user.Role;
import com.dippie.model.user.User;
import com.dippie.security.Viewer;

import org.springframework.stereotype.Component;

import java.util.Objects;

// tách luật chặn quản trị tài khoản ra riêng khỏi service để dễ đọc với dễ viết unit test
@Component
public class AdminUserPolicy {

    public static final String SELF_CHANGE_MESSAGE = "Bạn không thể tự đổi quyền hoặc khóa tài khoản của chính mình.";
    public static final String LAST_ADMIN_MESSAGE = "Hệ thống cần giữ lại ít nhất một quản trị viên đang hoạt động.";
    private static final String INVALID_ROLE_MESSAGE = "Vai trò không hợp lệ.";

    // đổi chuỗi role sang enum Role, sai hay trống thì ném 400
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

    // chặn tự đổi quyền chính tài khoản đó và chặn hạ quyền admin đang bật cuối cùng
    public void ensureCanChangeRole(Viewer actor, User target, Role newRole, long enabledAdminCount) {
        ensureNotSelf(actor, target);
        if (target.isAdmin() && target.isEnabled() && newRole != Role.ROLE_ADMIN && enabledAdminCount <= 1) {
            throw new ConflictException(LAST_ADMIN_MESSAGE);
        }
    }

    // chặn tự khóa chính tài khoản đó và chặn khóa admin đang bật cuối cùng
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
