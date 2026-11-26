package com.dippie.service.admin;

import com.dippie.model.catalog.CatalogImportedEvent;
import com.dippie.model.user.User;
import com.dippie.repository.user.UserRepository;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

// Observer lắng nghe CatalogImportedEvent qua @EventListener để ghi nhật ký quản trị,
// giúp luồng nhập danh mục bên gói catalog không phải phụ thuộc ngược vào gói admin.
@Component
public class CatalogImportAuditListener {

    private final UserRepository userRepository;
    private final AdminAuditService adminAuditService;

    public CatalogImportAuditListener(UserRepository userRepository, AdminAuditService adminAuditService) {
        this.userRepository = userRepository;
        this.adminAuditService = adminAuditService;
    }

    // Khi danh mục nhập xong, tra lại id người thực hiện nếu có rồi lưu thông tin gói và snapshot vào nhật ký.
    @EventListener
    public void onCatalogImported(CatalogImportedEvent event) {
        // Sự kiện không có tên người nhập thì gán về system; có tên thì tra thêm userId nếu tài khoản còn tồn tại.
        String username = (event.importedBy() == null || event.importedBy().isBlank())
                ? AdminAuditService.SYSTEM_ACTOR
                : event.importedBy().trim();
        Long actorUserId = AdminAuditService.SYSTEM_ACTOR.equals(username)
                ? null
                : userRepository.findByUsername(username).map(User::getId).orElse(null);

        // Gom mã gói, revision và thông tin snapshot vào details JSON của dòng nhật ký.
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
