package com.dippie.service.admin;

import com.dippie.dto.admin.AdminAuditEventView;
import com.dippie.dto.admin.AdminPageResponse;
import com.dippie.model.admin.AdminAuditEvent;
import com.dippie.repository.admin.AdminAuditEventRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

// ghi với đọc nhật ký quản trị, gom chung 1 format để các luồng log đều nhau
@Service
public class AdminAuditService {

    // mấy mã hành động cố định cho thao tác quản trị
    public enum Action {
        USER_ROLE_CHANGED,
        USER_DISABLED,
        USER_ENABLED,
        CATALOG_IMPORTED,
        CATALOG_SNAPSHOT_RETRIED,
        CATALOG_EXPORTED,
        ADMIN_PASSWORD_CHANGED,
        ADMIN_BOOTSTRAP_UPDATED
    }

    // loại đối tượng bị tác động trong từng dòng log
    public enum TargetType {
        USER, CATALOG_PACKAGE, CATALOG_SNAPSHOT, PLAYBACK_REPORT
    }

    public static final String SYSTEM_ACTOR = "system";
    private static final int MAX_USERNAME_LENGTH = 50;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final AdminAuditEventRepository auditEventRepository;
    private final CurrentViewer currentViewer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdminAuditService(AdminAuditEventRepository auditEventRepository, CurrentViewer currentViewer,
            ObjectMapper objectMapper, Clock clock) {
        this.auditEventRepository = auditEventRepository;
        this.currentViewer = currentViewer;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    // ghi log theo admin đang đăng nhập dùng enum Action
    @Transactional
    public void record(Viewer actor, Action action, TargetType targetType, String targetId, Map<String, Object> details) {
        record(actor, action.name(), targetType.name(), targetId, details);
    }

    // bản nhận action dạng chuỗi cho các gói khác tự mở rộng, actor null thì ghi tên system
    @Transactional
    public void record(Viewer actor, String action, String targetType, String targetId, Map<String, Object> details) {
        recordForActor(actor == null ? null : actor.userId(), actor == null ? SYSTEM_ACTOR : actor.username(),
                action, targetType, targetId, details);
    }

    // ghi thẳng theo userId với username lúc chỗ gọi không có sẵn Viewer
    @Transactional
    public void recordForActor(Long actorUserId, String actorUsername, Action action, TargetType targetType,
            String targetId, Map<String, Object> details) {
        recordForActor(actorUserId, actorUsername, action.name(), targetType.name(), targetId, details);
    }

    // dùng saveAndFlush để dòng audit đi chung transaction với thao tác chính luôn
    @Transactional
    public void recordForActor(Long actorUserId, String actorUsername, String action, String targetType,
            String targetId, Map<String, Object> details) {
        AdminAuditEvent event = new AdminAuditEvent(actorUserId, normalizeUsername(actorUsername), action,
                targetType, targetId, serializeDetails(details), LocalDateTime.now(clock));
        auditEventRepository.saveAndFlush(event);
    }

    // check quyền admin ngay tầng service rồi mới lấy log mới nhất
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

    // dữ liệu cũ hay JSON lỗi thì trả Map rỗng kẻo hỏng cả trang nhật ký vì 1 dòng
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

    // cắt username quá 50 ký tự cho vừa cột DB, trống thì gán system
    private static String normalizeUsername(String rawUsername) {
        if (rawUsername == null || rawUsername.isBlank()) {
            return SYSTEM_ACTOR;
        }
        String trimmed = rawUsername.trim();
        return trimmed.length() > MAX_USERNAME_LENGTH ? trimmed.substring(0, MAX_USERNAME_LENGTH) : trimmed;
    }
}
