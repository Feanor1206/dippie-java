package com.dippie.desktop.model;

import java.time.LocalDateTime;

// 1 dòng nhật ký trong admin_audit_events, giữ y nguyên mấy trường trang quản trị web đang đọc
public record AuditEvent(long id, Long actorUserId, String actorUsername, String action,
                         String targetType, String targetId, String detailsJson, LocalDateTime createdAt) {
}
