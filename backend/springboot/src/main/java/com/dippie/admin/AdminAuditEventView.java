package com.dippie.admin;

import java.time.LocalDateTime;
import java.util.Map;

public record AdminAuditEventView(
        Long id,
        String actorUsername,
        String action,
        String targetType,
        String targetId,
        Map<String, Object> details,
        LocalDateTime createdAt
) {
}
