package com.dippie.service.admin;

import com.dippie.security.Viewer;

import org.springframework.stereotype.Component;

import java.util.Map;

// cổng Facade cho mấy service ngoài gói admin như báo lỗi phát hay quên mật khẩu ghi log mà khỏi lộ AdminAuditService
@Component
public class AuditTrailWriter {

    private final AdminAuditService adminAuditService;

    public AuditTrailWriter(AdminAuditService adminAuditService) {
        this.adminAuditService = adminAuditService;
    }

    // ghi log gắn với user đang đăng nhập
    public void record(
            Viewer actor,
            String action,
            String targetType,
            String targetId,
            Map<String, Object> details
    ) {
        adminAuditService.record(actor, action, targetType, targetId, details);
    }

    // ghi log bằng userId với username truyền thẳng lúc không có sẵn Viewer
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
