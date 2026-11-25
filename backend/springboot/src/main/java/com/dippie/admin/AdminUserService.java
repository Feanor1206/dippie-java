package com.dippie.admin;

import com.dippie.common.error.ConflictException;
import com.dippie.common.error.InvalidRequestException;
import com.dippie.common.error.NotFoundException;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Role;
import com.dippie.security.Viewer;
import com.dippie.user.User;
import com.dippie.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Xử lý tìm kiếm, đổi quyền và bật tắt tài khoản, kèm khóa bản ghi trong DB để tránh hai admin hạ quyền lẫn nhau cùng lúc.
@Service
class AdminUserService {

    private final CurrentViewer currentViewer;
    private final UserRepository userRepository;
    private final AdminUserPolicy adminUserPolicy;
    private final AdminAuditService adminAuditService;
    private final Clock clock;

    AdminUserService(CurrentViewer currentViewer, UserRepository userRepository, AdminUserPolicy adminUserPolicy,
            AdminAuditService adminAuditService, Clock clock) {
        this.currentViewer = currentViewer;
        this.userRepository = userRepository;
        this.adminUserPolicy = adminUserPolicy;
        this.adminAuditService = adminAuditService;
        this.clock = clock;
    }

    // Kiểm tra quyền admin rồi tìm phân trang theo tên đăng nhập hoặc email không phân biệt hoa thường.
    @Transactional(readOnly = true)
    public AdminPageResponse<AdminUserView> listUsers(String query, int page, int size) {
        currentViewer.requireAdmin();
        var pageable = AdminPageResponse.pageRequest(page, size);
        String term = (query == null || query.isBlank()) ? null : query.trim().toLowerCase(Locale.ROOT);
        Page<AdminUserView> result = userRepository
                .searchByUsernameOrEmail(term, pageable)
                .map(AdminUserView::from);
        return AdminPageResponse.of(result);
    }

    // Khóa tài khoản đích, kiểm tra luật an toàn rồi cập nhật vai trò và ghi nhật ký.
    @Transactional
    public AdminUserView changeRole(Long targetUserId, UpdateUserRoleRequest request) {
        Viewer actor = currentViewer.requireAdmin();
        Role newRole = adminUserPolicy.parseRole(request == null ? null : request.role());
        LockedTarget locked = lockTargetForMutation(actor, targetUserId, newRole != Role.ROLE_ADMIN);
        User target = locked.target();
        adminUserPolicy.ensureCanChangeRole(actor, target, newRole, locked.enabledAdminCount());

        Role previousRole = target.getRole();
        // Vai trò không đổi thì trả luôn, khỏi sửa DB hay ghi thêm dòng nhật ký thừa.
        if (previousRole == newRole) {
            return AdminUserView.from(target);
        }
        target.changeRole(newRole, LocalDateTime.now(clock));
        User saved = userRepository.saveAndFlush(target);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("username", saved.getUsername());
        details.put("previousRole", previousRole.name());
        details.put("newRole", newRole.name());
        recordUserAudit(actor, saved, AdminAuditService.Action.USER_ROLE_CHANGED, details);
        return AdminUserView.from(saved);
    }

    // Khóa tài khoản đích, kiểm tra luật an toàn rồi bật hoặc khóa tài khoản kèm ghi nhật ký.
    @Transactional
    public AdminUserView changeStatus(Long targetUserId, UpdateUserStatusRequest request) {
        // Kiểm tra quyền admin và dữ liệu enabled trước khi đụng vào DB.
        Viewer actor = currentViewer.requireAdmin();
        if (request == null || request.enabled() == null) {
            throw new InvalidRequestException("Vui lòng chọn trạng thái bật hoặc khóa cho tài khoản.");
        }
        boolean targetEnabled = request.enabled();
        LockedTarget locked = lockTargetForMutation(actor, targetUserId, !targetEnabled);
        User target = locked.target();
        adminUserPolicy.ensureCanChangeStatus(actor, target, targetEnabled, locked.enabledAdminCount());

        // Trạng thái giữ nguyên thì trả luôn để không tăng token_version hay ghi log dư.
        boolean previousEnabled = target.isEnabled();
        if (previousEnabled == targetEnabled) {
            return AdminUserView.from(target);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (targetEnabled) {
            target.enable(now);
        } else {
            target.disable(now);
        }
        User saved = userRepository.saveAndFlush(target);

        AdminAuditService.Action action = targetEnabled
                ? AdminAuditService.Action.USER_ENABLED
                : AdminAuditService.Action.USER_DISABLED;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("username", saved.getUsername());
        details.put("previousEnabled", previousEnabled);
        details.put("enabled", targetEnabled);
        recordUserAudit(actor, saved, action, details);
        return AdminUserView.from(saved);
    }

    // Việt để ý đoạn khóa này: khi thao tác có nguy cơ làm giảm số admin đang mở, mình phải khóa toàn bộ
    // danh sách admin hoạt động cùng lúc để hai request song song không cùng thấy còn 2 admin rồi hạ quyền nhau sạch trơn.
    private LockedTarget lockTargetForMutation(Viewer actor, Long targetUserId, boolean mayReduceAdmins) {
        // Chặn tự sửa sớm để trả 409 ngay, rồi đọc thử bản ghi đích xem có tồn tại không.
        if (Objects.equals(actor.userId(), targetUserId)) {
            throw new ConflictException(AdminUserPolicy.SELF_CHANGE_MESSAGE);
        }
        User existingTarget = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng."));
        // Khóa toàn bộ admin đang hoạt động khi thao tác có thể làm giảm số lượng admin.
        if (mayReduceAdmins && existingTarget.isAdmin() && existingTarget.isEnabled()) {
            List<User> enabledAdmins = userRepository.findEnabledByRoleForUpdate(Role.ROLE_ADMIN);
            User target = enabledAdmins.stream()
                    .filter(candidate -> candidate.getId().equals(targetUserId))
                    .findFirst()
                    .orElseGet(() -> userRepository.findByIdForUpdate(targetUserId)
                            .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng.")));
            return new LockedTarget(target, enabledAdmins.size());
        }
        // Không thuộc nhánh giảm admin thì khóa riêng bản ghi đích; nếu lúc khóa thấy vừa lên admin thì đếm lại cho chắc.
        User target = userRepository.findByIdForUpdate(targetUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng."));
        long adminCount = mayReduceAdmins && target.isAdmin() && target.isEnabled()
                ? userRepository.findEnabledByRoleForUpdate(Role.ROLE_ADMIN).size()
                : Long.MAX_VALUE;
        return new LockedTarget(target, adminCount);
    }

    private void recordUserAudit(Viewer actor, User saved, AdminAuditService.Action action, Map<String, Object> details) {
        adminAuditService.record(actor, action, AdminAuditService.TargetType.USER, String.valueOf(saved.getId()), details);
    }

    // Giữ bản ghi user đã khóa kèm số admin đang hoạt động tại cùng thời điểm khóa.
    private record LockedTarget(User target, long enabledAdminCount) {
    }
}
