package com.dippie.desktop.service;

import com.dippie.desktop.dao.AuditDao;
import com.dippie.desktop.dao.UserDao;
import com.dippie.desktop.db.DataAccessException;
import com.dippie.desktop.db.JdbcDatabase;
import com.dippie.desktop.model.AdminSession;
import com.dippie.desktop.model.Role;
import com.dippie.desktop.model.UserAccount;

import java.util.List;

// service quản lý tài khoản, check quy tắc an toàn xong lưu thay đổi kèm audit log trong 1 transaction
public final class UserService {
    private final JdbcDatabase db;
    private final UserDao userDao;
    private final AuditDao auditDao;

    public UserService(JdbcDatabase db, UserDao userDao, AuditDao auditDao) {
        this.db = db;
        this.userDao = userDao;
        this.auditDao = auditDao;
    }

    public List<UserAccount> list() {
        return userDao.findAll();
    }

    // khóa danh sách admin đang hoạt động trước kẻo 2 phiên cùng lúc hạ quyền mất ông admin cuối cùng
    public UserAccount change(AdminSession session, UserAccount selected, Role role, boolean enabled) {
        return db.transaction(connection -> {
            var enabledAdmins = userDao.lockEnabledAdmins(connection);
            UserAccount actor = AdminQuery.requireCurrent(session, enabledAdmins.stream()
                    .filter(account -> account.id() == session.userId()).findFirst().orElse(null));
            if (selected.id() == actor.id()) {
                throw new DataAccessException.Conflict("Bạn không thể tự đổi vai trò hay khóa tài khoản của chính mình.");
            }

            UserAccount current = userDao.lockAccount(connection, selected.id())
                    .orElseThrow(() -> new DataAccessException.Conflict("Tài khoản này không còn tồn tại. Hãy làm mới bảng."));
            verifyChange(current, selected, role, enabled, enabledAdmins.size());

            // lưu thay đổi, tăng token_version cho phiên web cũ hết xài rồi ghi audit log
            if (userDao.updateAccess(connection, current.id(), role, enabled) != 1) {
                throw new DataAccessException.Conflict("Chưa cập nhật được tài khoản này.");
            }
            String action = current.role() != role ? "USER_ROLE_CHANGED" : enabled ? "USER_ENABLED" : "USER_DISABLED";
            String details = """
                    {"previousRole":"%s","newRole":"%s","previousEnabled":%b,"enabled":%b}
                    """.formatted(current.role().name(), role.name(), current.enabled(), enabled).strip();
            auditDao.insert(connection, actor.id(), actor.username(), action, "USER", Long.toString(current.id()), details);
            return userDao.findById(connection, current.id()).orElseThrow();
        });
    }

    // so tokenVersion, vai trò với trạng thái để lệnh cũ hay nút Hoàn tác không ghi đè thay đổi mới hơn từ phiên khác
    private static void verifyChange(UserAccount current, UserAccount selected, Role role,
                                     boolean enabled, int enabledAdminCount) {
        if (current.tokenVersion() != selected.tokenVersion()
                || current.role() != selected.role() || current.enabled() != selected.enabled()) {
            throw new DataAccessException.Conflict("Tài khoản này vừa được chỉnh sửa ở nơi khác. Hãy làm mới bảng rồi thử lại.");
        }
        if (current.role() == Role.ROLE_ADMIN && current.enabled()
                && (role != Role.ROLE_ADMIN || !enabled) && enabledAdminCount <= 1) {
            throw new DataAccessException.Conflict("Cần giữ lại ít nhất một quản trị viên đang hoạt động.");
        }
        boolean changesRole = current.role() != role;
        boolean changesStatus = current.enabled() != enabled;
        if (role == null || changesRole == changesStatus) {
            throw new DataAccessException.Conflict("Mỗi lần chỉ đổi vai trò hoặc khóa/mở khóa tài khoản.");
        }
    }
}
