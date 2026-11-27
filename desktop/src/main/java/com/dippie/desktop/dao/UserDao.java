package com.dippie.desktop.dao;

import com.dippie.desktop.db.JdbcDatabase;
import com.dippie.desktop.db.UtcTime;
import com.dippie.desktop.model.Role;
import com.dippie.desktop.model.UserAccount;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

// DAO bảng users, lo đọc danh sách user, tra mật khẩu băm lúc đăng nhập và đổi quyền hay khóa/mở khóa
public final class UserDao extends AbstractJdbcDao<UserAccount, Long> {
    private static final String COLUMNS = "id, username, email, full_name, role, enabled, token_version";

    public UserDao(JdbcDatabase db) {
        super(db, "users", COLUMNS, UserDao::mapAccount);
    }

    // tìm được theo cả username lẫn email giống bên web, tách mật khẩu băm khỏi UserAccount kẻo mang lên bảng giao diện
    public Optional<Credentials> findCredentials(String identifier) {
        return db.read(connection -> first(query(connection, """
                SELECT %s, password FROM users
                WHERE username = ? OR email = ?
                ORDER BY id LIMIT 1
                """.formatted(COLUMNS), row -> new Credentials(mapAccount(row), row.getString("password")),
                identifier, identifier)));
    }

    public Optional<UserAccount> lockAccount(Connection connection, long id) throws SQLException {
        return lockById(connection, id);
    }

    // khóa mấy admin đang bật theo thứ tự id tăng dần khớp bên web kẻo deadlock lúc đổi quyền song song
    public List<UserAccount> lockEnabledAdmins(Connection connection) throws SQLException {
        return query(connection, "SELECT " + COLUMNS + """
                 FROM users WHERE role = ? AND enabled = TRUE ORDER BY id FOR UPDATE
                """, UserDao::mapAccount, Role.ROLE_ADMIN.name());
    }

    // Việt đừng bỏ token_version = token_version + 1 nha, thiếu là token cũ bên web vẫn xài được sau khi khóa hay hạ quyền
    public int updateAccess(Connection connection, long id, Role role, boolean enabled) throws SQLException {
        return update(connection, """
                UPDATE users SET role = ?, enabled = ?, token_version = token_version + 1,
                                 updated_at = ?
                WHERE id = ?
                """, role.name(), enabled, UtcTime.now(), id);
    }

    private static UserAccount mapAccount(ResultSet row) throws SQLException {
        String role = row.getString("role");
        try {
            return new UserAccount(row.getLong("id"), row.getString("username"), row.getString("email"),
                    row.getString("full_name"), Role.valueOf(role), row.getBoolean("enabled"),
                    row.getLong("token_version"));
        } catch (IllegalArgumentException exception) {
            throw new SQLException("Vai trò tài khoản trong cơ sở dữ liệu không hợp lệ.", exception);
        }
    }

    // chỉ dùng lúc đăng nhập để mang mật khẩu băm, ghi đè toString() che mã băm đi
    public record Credentials(UserAccount account, String passwordHash) {
        @Override
        public String toString() {
            return "Thông tin xác thực tài khoản " + account.id();
        }
    }
}
