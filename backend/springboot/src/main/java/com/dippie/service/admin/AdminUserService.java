package com.dippie.service.admin;

import com.dippie.dto.admin.AdminPageResponse;
import com.dippie.dto.admin.AdminUserView;
import com.dippie.dto.admin.UpdateUserRoleRequest;
import com.dippie.dto.admin.UpdateUserStatusRequest;
import com.dippie.exception.ConflictException;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.NotFoundException;
import com.dippie.model.user.Role;
import com.dippie.model.user.User;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

// tìm user, đổi quyền, bật tắt tài khoản, có khóa bản ghi trong DB kẻo 2 admin hạ quyền nhau cùng lúc
@Service
public class AdminUserService {

    private final CurrentViewer currentViewer;
    private final UserRepository userRepository;
    private final AdminUserPolicy adminUserPolicy;
    private final AdminAuditService adminAuditService;
    private final Clock clock;

    public AdminUserService(CurrentViewer currentViewer, UserRepository userRepository, AdminUserPolicy adminUserPolicy,
            AdminAuditService adminAuditService, Clock clock) {
        this.currentViewer = currentViewer;
        this.userRepository = userRepository;
        this.adminUserPolicy = adminUserPolicy;
        this.adminAuditService = adminAuditService;
        this.clock = clock;
    }

    // check admin rồi tìm phân trang theo username hoặc email không phân biệt hoa thường
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

    // khóa user đích, check luật an toàn rồi đổi role kèm ghi audit
    @Transactional
    public AdminUserView changeRole(Long targetUserId, UpdateUserRoleRequest request) {
        Viewer actor = currentViewer.requireAdmin();
        Role newRole = adminUserPolicy.parseRole(request == null ? null : request.role());
        LockedTarget locked = lockTargetForMutation(actor, targetUserId, newRole != Role.ROLE_ADMIN);
        User target = locked.target();
        adminUserPolicy.ensureCanChangeRole(actor, target, newRole, locked.enabledAdminCount());

        Role previousRole = target.getRole();
        // role y cũ thì trả luôn, khỏi đụng DB hay ghi log thừa
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

    // khóa user đích, check luật an toàn rồi bật tắt tài khoản kèm ghi audit
    @Transactional
    public AdminUserView changeStatus(Long targetUserId, UpdateUserStatusRequest request) {
        // check admin với trường enabled trước khi đụng DB
        Viewer actor = currentViewer.requireAdmin();
        if (request == null || request.enabled() == null) {
            throw new InvalidRequestException("Vui lòng chọn trạng thái bật hoặc khóa cho tài khoản.");
        }
        boolean targetEnabled = request.enabled();
        LockedTarget locked = lockTargetForMutation(actor, targetUserId, !targetEnabled);
        User target = locked.target();
        adminUserPolicy.ensureCanChangeStatus(actor, target, targetEnabled, locked.enabledAdminCount());

        // trạng thái y cũ thì trả luôn kẻo tăng oan token_version với ghi log dư
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

    // Việt để ý đoạn khóa này nha, hễ thao tác có nguy cơ làm giảm số admin đang bật thì phải khóa hết
    // cả list admin đang bật cùng lúc kẻo 2 request song song cùng thấy còn 2 admin rồi hạ nhau sạch trơn
    private LockedTarget lockTargetForMutation(Viewer actor, Long targetUserId, boolean mayReduceAdmins) {
        // chặn tự sửa sớm để trả 409 ngay, rồi mới check xem user đích có tồn tại không
        if (Objects.equals(actor.userId(), targetUserId)) {
            throw new ConflictException(AdminUserPolicy.SELF_CHANGE_MESSAGE);
        }
        User existingTarget = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng."));
        // khóa hết admin đang bật nếu thao tác này có thể làm giảm số admin
        if (mayReduceAdmins && existingTarget.isAdmin() && existingTarget.isEnabled()) {
            List<User> enabledAdmins = userRepository.findEnabledByRoleForUpdate(Role.ROLE_ADMIN);
            User target = enabledAdmins.stream()
                    .filter(candidate -> candidate.getId().equals(targetUserId))
                    .findFirst()
                    .orElseGet(() -> userRepository.findByIdForUpdate(targetUserId)
                            .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng.")));
            return new LockedTarget(target, enabledAdmins.size());
        }
        // không giảm admin thì khóa riêng user đích thôi, lỡ lúc khóa thấy nó vừa lên admin thì đếm lại cho chắc
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

    // gói user đã khóa kèm số admin đang bật tại đúng lúc khóa
    private record LockedTarget(User target, long enabledAdminCount) {
    }
}
