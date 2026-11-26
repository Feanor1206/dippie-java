package com.dippie.dto.admin;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// mỗi dòng log cho trang admin sau khi đọc detailsJson thành Map
// compact constructor copy Map sang chỉ đọc kẻo bên gọi lỡ sửa thẳng vào
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
