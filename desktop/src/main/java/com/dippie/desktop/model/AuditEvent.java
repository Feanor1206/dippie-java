package com.dippie.desktop.model;

import java.time.LocalDateTime;

// Một dòng nhật ký trong bảng admin_audit_events, giữ nguyên các trường mà trang quản trị web đang đọc.
public record AuditEvent(long id, Long actorUserId, String actorUsername, String action,
                         String targetType, String targetId, String detailsJson, LocalDateTime createdAt) {
}
