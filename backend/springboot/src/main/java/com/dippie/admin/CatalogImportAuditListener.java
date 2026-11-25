package com.dippie.admin;

import com.dippie.catalog.importing.CatalogImportedEvent;
import com.dippie.user.User;
import com.dippie.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CatalogImportAuditListener {

    private static final String TARGET_TYPE_CATALOG_PACKAGE = "CATALOG_PACKAGE";
    private static final String SYSTEM_ACTOR = "system";

    private final UserRepository userRepository;
    private final AdminAuditService adminAuditService;

    public CatalogImportAuditListener(UserRepository userRepository, AdminAuditService adminAuditService) {
        this.userRepository = userRepository;
        this.adminAuditService = adminAuditService;
    }

    @EventListener
    public void onCatalogImported(CatalogImportedEvent event) {
        String username = (event.importedBy() == null || event.importedBy().isBlank())
                ? SYSTEM_ACTOR
                : event.importedBy().trim();
        Long actorUserId = userRepository.findByUsername(username)
                .map(User::getId)
                .orElse(null);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("packageId", event.packageId());
        details.put("revision", event.revision());
        details.put("snapshotVersion", event.snapshotVersion());
        details.put("snapshotId", event.snapshotId());
        if (event.importedAt() != null) {
            details.put("importedAt", event.importedAt().toString());
        }

        String targetId = event.packageId() != null ? event.packageId() : String.valueOf(event.snapshotId());
        adminAuditService.recordForActor(
                actorUserId,
                username,
                AdminAuditService.ACTION_CATALOG_IMPORTED,
                TARGET_TYPE_CATALOG_PACKAGE,
                targetId,
                details
        );
    }
}
