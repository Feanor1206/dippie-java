package com.dippie.admin;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

record AdminAuditEventView(
        Long id,
        String actorUsername,
        String action,
        String targetType,
        String targetId,
        Map<String, Object> details,
        LocalDateTime createdAt
) {

    AdminAuditEventView {
        details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }
}
