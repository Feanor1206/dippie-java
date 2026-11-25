package com.dippie.desktop.dao;

import com.dippie.desktop.db.JdbcDatabase;
import com.dippie.desktop.model.AuditEvent;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

// DAO ghi các thao tác quản trị vào bảng admin_audit_events dùng chung với bên web.
public final class AuditDao extends AbstractJdbcDao<AuditEvent, Long> {
    public AuditDao(JdbcDatabase db) {
        super(db, "admin_audit_events", """
                id, actor_user_id, actor_username, action, target_type, target_id, details_json, created_at
                """, row -> new AuditEvent(row.getLong("id"), row.getObject("actor_user_id", Long.class),
                row.getString("actor_username"), row.getString("action"), row.getString("target_type"),
                row.getString("target_id"), row.getString("details_json"),
                row.getObject("created_at", LocalDateTime.class)));
    }

    // Phải dùng chung Connection với thao tác gốc để nếu ghi nhật ký bị lỗi thì cả giao dịch rollback theo.
    public void insert(Connection connection, long actorId, String actorUsername,
                       String action, String targetType, String targetId, String detailsJson) throws SQLException {
        int inserted = update(connection, """
                INSERT INTO admin_audit_events
                    (actor_user_id, actor_username, action, target_type, target_id, details_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, actorId, actorUsername, action, targetType, targetId, detailsJson);
        if (inserted != 1) {
            throw new SQLException("Không lưu được dòng nhật ký quản trị.");
        }
    }
}
