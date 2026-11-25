package com.dippie.admin;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String TARGET_TYPE_USER = "USER";

    private final CurrentViewer currentViewer;
    private final UserRepository userRepository;
    private final AdminUserPolicy adminUserPolicy;
    private final AdminAuditService adminAuditService;
    private final Clock clock;

    public AdminUserService(
            CurrentViewer currentViewer,
            UserRepository userRepository,
            AdminUserPolicy adminUserPolicy,
            AdminAuditService adminAuditService,
            Clock clock
    ) {
        this.currentViewer = currentViewer;
        this.userRepository = userRepository;
        this.adminUserPolicy = adminUserPolicy;
        this.adminAuditService = adminAuditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminUserView> listUsers(String query, int page, int size) {
        currentViewer.requireAdmin();
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("Tham số phân trang không hợp lệ.");
        }
        String term = (query == null || query.isBlank()) ? null : query.trim().toLowerCase(Locale.ROOT);
        Page<AdminUserView> result = userRepository
                .searchByUsernameOrEmail(term, PageRequest.of(page, size))
                .map(AdminUserView::from);
        return AdminPageResponse.of(result);
    }

    @Transactional
    public AdminUserView changeRole(Long targetUserId, UpdateUserRoleRequest request) {
        Viewer actor = currentViewer.requireAdmin();
        Role newRole = adminUserPolicy.parseRole(request == null ? null : request.role());

        List<User> enabledAdmins = userRepository.findEnabledByRoleForUpdate(Role.ROLE_ADMIN);
        User target = resolveLockedTarget(targetUserId, enabledAdmins);
        adminUserPolicy.ensureCanChangeRole(actor, target, newRole, enabledAdmins.size());

        Role previousRole = target.getRole();
        target.changeRole(newRole, LocalDateTime.now(clock));
        User saved = userRepository.saveAndFlush(target);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("username", saved.getUsername());
        details.put("previousRole", previousRole.name());
        details.put("newRole", newRole.name());
        adminAuditService.record(
                actor,
                AdminAuditService.ACTION_USER_ROLE_CHANGED,
                TARGET_TYPE_USER,
                String.valueOf(saved.getId()),
                details
        );

        return AdminUserView.from(saved);
    }

    @Transactional
    public AdminUserView changeStatus(Long targetUserId, UpdateUserStatusRequest request) {
        Viewer actor = currentViewer.requireAdmin();
        if (request == null || request.enabled() == null) {
            throw new InvalidRequestException("Trạng thái tài khoản không được để trống.");
        }
        boolean targetEnabled = request.enabled();

        List<User> enabledAdmins = userRepository.findEnabledByRoleForUpdate(Role.ROLE_ADMIN);
        User target = resolveLockedTarget(targetUserId, enabledAdmins);
        adminUserPolicy.ensureCanChangeStatus(actor, target, targetEnabled, enabledAdmins.size());

        boolean previousEnabled = target.isEnabled();
        LocalDateTime now = LocalDateTime.now(clock);
        if (targetEnabled) {
            target.enable(now);
        } else {
            target.disable(now);
        }
        User saved = userRepository.saveAndFlush(target);

        String action = targetEnabled
                ? AdminAuditService.ACTION_USER_ENABLED
                : AdminAuditService.ACTION_USER_DISABLED;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("username", saved.getUsername());
        details.put("previousEnabled", previousEnabled);
        details.put("enabled", targetEnabled);
        adminAuditService.record(
                actor,
                action,
                TARGET_TYPE_USER,
                String.valueOf(saved.getId()),
                details
        );

        return AdminUserView.from(saved);
    }

    private User resolveLockedTarget(Long targetUserId, List<User> enabledAdmins) {
        return enabledAdmins.stream()
                .filter(candidate -> candidate.getId().equals(targetUserId))
                .findFirst()
                .orElseGet(() -> userRepository.findByIdForUpdate(targetUserId)
                        .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng.")));
    }
}
