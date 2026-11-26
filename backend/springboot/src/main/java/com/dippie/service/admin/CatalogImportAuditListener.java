package com.dippie.service.admin;

import com.dippie.model.catalog.CatalogImportedEvent;
import com.dippie.model.user.User;
import com.dippie.repository.user.UserRepository;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

// kiểu Observer nghe CatalogImportedEvent qua @EventListener để ghi log quản trị
// nhờ vậy luồng import bên gói catalog khỏi phải phụ thuộc ngược vào gói admin
@Component
public class CatalogImportAuditListener {

    private final UserRepository userRepository;
    private final AdminAuditService adminAuditService;

    public CatalogImportAuditListener(UserRepository userRepository, AdminAuditService adminAuditService) {
        this.userRepository = userRepository;
        this.adminAuditService = adminAuditService;
    }

    // import xong thì tra lại id người chạy nếu có rồi lưu thông tin gói với snapshot vào audit
    @EventListener
    public void onCatalogImported(CatalogImportedEvent event) {
        // không có tên người import thì gán system, có thì tra thêm userId nếu tài khoản còn đó
        String username = (event.importedBy() == null || event.importedBy().isBlank())
                ? AdminAuditService.SYSTEM_ACTOR
                : event.importedBy().trim();
        Long actorUserId = AdminAuditService.SYSTEM_ACTOR.equals(username)
                ? null
                : userRepository.findByUsername(username).map(User::getId).orElse(null);

        // gom packageId, revision với thông tin snapshot vô details JSON của dòng log
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("packageId", event.packageId());
        details.put("revision", event.revision());
        details.put("snapshotVersion", event.snapshotVersion());
        details.put("snapshotId", event.snapshotId());
        if (event.importedAt() != null) {
            details.put("importedAt", event.importedAt().toString());
        }

        String targetId = event.packageId() != null ? event.packageId() : String.valueOf(event.snapshotId());
        adminAuditService.recordForActor(actorUserId, username, AdminAuditService.Action.CATALOG_IMPORTED,
                AdminAuditService.TargetType.CATALOG_PACKAGE, targetId, details);
    }
}
