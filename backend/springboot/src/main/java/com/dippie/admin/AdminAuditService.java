package com.dippie.admin;

import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AdminAuditService {

    static final String ACTION_USER_ROLE_CHANGED = "USER_ROLE_CHANGED";
    static final String ACTION_USER_DISABLED = "USER_DISABLED";
    static final String ACTION_USER_ENABLED = "USER_ENABLED";
    static final String ACTION_CATALOG_IMPORTED = "CATALOG_IMPORTED";

    private static final int MAX_USERNAME_LENGTH = 50;
    private static final String SYSTEM_ACTOR = "system";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AdminAuditEventRepository auditEventRepository;
    private final CurrentViewer currentViewer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    AdminAuditService(
            AdminAuditEventRepository auditEventRepository,
            CurrentViewer currentViewer,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.auditEventRepository = auditEventRepository;
        this.currentViewer = currentViewer;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void record(
            Viewer actor,
            String action,
            String targetType,
            String targetId,
            Map<String, Object> details
    ) {
        Long actorUserId = actor == null ? null : actor.userId();
        String actorUsername = actor == null ? SYSTEM_ACTOR : actor.username();
        recordForActor(actorUserId, actorUsername, action, targetType, targetId, details);
    }

    @Transactional
    public void recordForActor(
            Long actorUserId,
            String actorUsername,
            String action,
            String targetType,
            String targetId,
            Map<String, Object> details
    ) {
        String normalizedUsername = normalizeUsername(actorUsername);
        String serializedDetails = serializeDetails(details);
        AdminAuditEvent event = new AdminAuditEvent(
                actorUserId,
                normalizedUsername,
                action,
                targetType,
                targetId,
                serializedDetails,
                LocalDateTime.now(clock)
        );
        auditEventRepository.saveAndFlush(event);
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminAuditEventView> listEvents(int page, int size) {
        currentViewer.requireAdmin();
        Page<AdminAuditEventView> result = auditEventRepository
                .findAllByOrderByCreatedAtDescIdDesc(AdminPageResponse.pageRequest(page, size))
                .map(this::toView);
        return AdminPageResponse.of(result);
    }

    private AdminAuditEventView toView(AdminAuditEvent event) {
        return new AdminAuditEventView(
                event.getId(),
                event.getActorUsername(),
                event.getAction(),
                event.getTargetType(),
                event.getTargetId(),
                parseDetails(event.getDetailsJson()),
                event.getCreatedAt()
        );
    }

    private String serializeDetails(Map<String, Object> details) {
        Map<String, Object> safe = details == null ? Map.of() : details;
        try {
            return objectMapper.writeValueAsString(safe);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể tuần tự hóa chi tiết nhật ký kiểm toán.", ex);
        }
    }

    private Map<String, Object> parseDetails(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(detailsJson, MAP_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private static String normalizeUsername(String rawUsername) {
        if (rawUsername == null || rawUsername.isBlank()) {
            return SYSTEM_ACTOR;
        }
        String trimmed = rawUsername.trim();
        return trimmed.length() > MAX_USERNAME_LENGTH ? trimmed.substring(0, MAX_USERNAME_LENGTH) : trimmed;
    }
}
