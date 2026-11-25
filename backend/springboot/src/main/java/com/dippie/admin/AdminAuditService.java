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

// Service ghi và đọc nhật ký quản trị, gom chung một định dạng để mọi luồng ghi log dùng nhất quán.
@Service
class AdminAuditService {

    // Các mã hành động cố định cho những thao tác quản trị trong hệ thống.
    enum Action {
        USER_ROLE_CHANGED,
        USER_DISABLED,
        USER_ENABLED,
        CATALOG_IMPORTED,
        CATALOG_SNAPSHOT_RETRIED,
        CATALOG_EXPORTED,
        ADMIN_PASSWORD_CHANGED,
        ADMIN_BOOTSTRAP_UPDATED
    }

    // Loại đối tượng bị tác động trong mỗi dòng nhật ký.
    enum TargetType {
        USER, CATALOG_PACKAGE, CATALOG_SNAPSHOT, PLAYBACK_REPORT
    }

    static final String SYSTEM_ACTOR = "system";
    private static final int MAX_USERNAME_LENGTH = 50;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AdminAuditEventRepository auditEventRepository;
    private final CurrentViewer currentViewer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    AdminAuditService(AdminAuditEventRepository auditEventRepository, CurrentViewer currentViewer,
            ObjectMapper objectMapper, Clock clock) {
        this.auditEventRepository = auditEventRepository;
        this.currentViewer = currentViewer;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    // Ghi log từ tài khoản admin đang đăng nhập bằng enum Action.
    @Transactional
    public void record(Viewer actor, Action action, TargetType targetType, String targetId, Map<String, Object> details) {
        record(actor, action.name(), targetType.name(), targetId, details);
    }

    // Bản nhận mã hành động dạng chuỗi để các gói tính năng khác tự mở rộng; nếu actor null thì ghi dưới tên system.
    @Transactional
    public void record(Viewer actor, String action, String targetType, String targetId, Map<String, Object> details) {
        recordForActor(actor == null ? null : actor.userId(), actor == null ? SYSTEM_ACTOR : actor.username(),
                action, targetType, targetId, details);
    }

    // Ghi trực tiếp theo userId và username khi caller không có sẵn đối tượng Viewer.
    @Transactional
    public void recordForActor(Long actorUserId, String actorUsername, Action action, TargetType targetType,
            String targetId, Map<String, Object> details) {
        recordForActor(actorUserId, actorUsername, action.name(), targetType.name(), targetId, details);
    }

    // Lưu ngay bằng saveAndFlush để bản ghi nhật ký đi cùng giao dịch với thao tác chính.
    @Transactional
    public void recordForActor(Long actorUserId, String actorUsername, String action, String targetType,
            String targetId, Map<String, Object> details) {
        AdminAuditEvent event = new AdminAuditEvent(actorUserId, normalizeUsername(actorUsername), action,
                targetType, targetId, serializeDetails(details), LocalDateTime.now(clock));
        auditEventRepository.saveAndFlush(event);
    }

    // Kiểm tra quyền admin ngay ở tầng service rồi mới lấy danh sách sự kiện mới nhất.
    @Transactional(readOnly = true)
    public AdminPageResponse<AdminAuditEventView> listEvents(int page, int size) {
        currentViewer.requireAdmin();
        Page<AdminAuditEventView> result = auditEventRepository
                .findAllByOrderByCreatedAtDescIdDesc(AdminPageResponse.pageRequest(page, size)).map(this::toView);
        return AdminPageResponse.of(result);
    }

    private AdminAuditEventView toView(AdminAuditEvent event) {
        return new AdminAuditEventView(event.getId(), event.getActorUsername(), event.getAction(), event.getTargetType(),
                event.getTargetId(), parseDetails(event.getDetailsJson()), event.getCreatedAt());
    }

    private String serializeDetails(Map<String, Object> details) {
        Map<String, Object> detailsMap = details == null ? Map.of() : details;
        try {
            return objectMapper.writeValueAsString(detailsMap);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể chuyển chi tiết nhật ký kiểm toán sang JSON.", ex);
        }
    }

    // Dữ liệu cũ hoặc JSON lỗi thì trả Map rỗng để cả trang nhật ký không hỏng theo một dòng.
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

    // Cắt username vượt 50 ký tự cho khớp cột DB, còn trống thì gán về system.
    private static String normalizeUsername(String rawUsername) {
        if (rawUsername == null || rawUsername.isBlank()) {
            return SYSTEM_ACTOR;
        }
        String trimmed = rawUsername.trim();
        return trimmed.length() > MAX_USERNAME_LENGTH ? trimmed.substring(0, MAX_USERNAME_LENGTH) : trimmed;
    }
}
