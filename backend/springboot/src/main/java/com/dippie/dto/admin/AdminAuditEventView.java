package com.dippie.dto.admin;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// Dữ liệu từng dòng nhật ký trả cho giao diện admin sau khi đã đọc detailsJson thành Map.
// Compact constructor sao chép Map thành dạng chỉ đọc để bên gọi không lỡ sửa trực tiếp.
public record AdminAuditEventView(
        Long id,
        String actorUsername,
        String action,
        String targetType,
        String targetId,
        Map<String, Object> details,
        LocalDateTime createdAt
) {

    public AdminAuditEventView {
        details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }
}
