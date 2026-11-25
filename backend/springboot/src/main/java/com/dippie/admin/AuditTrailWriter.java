package com.dippie.admin;

import com.dippie.security.Viewer;
import java.util.Map;
import org.springframework.stereotype.Component;

// Cổng Facade public để các gói khác như report hay passwordreset ghi nhật ký mà không cần lộ AdminAuditService ra ngoài gói admin.
@Component
public class AuditTrailWriter {

    private final AdminAuditService adminAuditService;

    public AuditTrailWriter(AdminAuditService adminAuditService) {
        this.adminAuditService = adminAuditService;
    }

    // Ghi sự kiện gắn với tài khoản đang đăng nhập.
    public void record(
            Viewer actor,
            String action,
            String targetType,
            String targetId,
            Map<String, Object> details
    ) {
        adminAuditService.record(actor, action, targetType, targetId, details);
    }

    // Ghi sự kiện với userId và username truyền thẳng, tiện dùng khi không có sẵn phiên Viewer.
    public void recordForActor(
            Long actorUserId,
            String actorUsername,
            String action,
            String targetType,
            String targetId,
            Map<String, Object> details
    ) {
        adminAuditService.recordForActor(actorUserId, actorUsername, action, targetType, targetId, details);
    }
}
