package com.dippie.service.account;

import com.dippie.dto.account.ChangePasswordRequest;
import com.dippie.dto.account.UpdateProfileRequest;
import com.dippie.dto.user.AuthResponse;
import com.dippie.dto.user.UserProfileResponse;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.TooManyRequestsException;
import com.dippie.model.user.User;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.JwtService;
import com.dippie.security.LoginRateLimiter;
import com.dippie.security.PasswordPolicy;
import com.dippie.service.admin.AuditTrailWriter;
import com.dippie.service.user.AuthService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

// Facade gom luồng tự sửa hồ sơ và tự đổi mật khẩu, có giới hạn tần suất theo IP + userId và ghi audit cho admin.
@Service
public class AccountService {

    public static final String ACTION_ADMIN_PASSWORD_CHANGED = "ADMIN_PASSWORD_CHANGED";
    private static final String TARGET_TYPE_USER = "USER";
    private static final String RATE_LIMIT_MESSAGE =
            "Bạn vừa thao tác quá nhiều lần. Hãy thử lại sau vài phút.";

    private final AuthService authService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;
    private final AuditTrailWriter auditTrailWriter;
    private final Clock clock;

    public AccountService(
            AuthService authService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginRateLimiter loginRateLimiter,
            AuditTrailWriter auditTrailWriter,
            Clock clock
    ) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginRateLimiter = loginRateLimiter;
        this.auditTrailWriter = auditTrailWriter;
        this.clock = clock;
    }

    // Cập nhật họ tên và email của chính người đang đăng nhập.
    // Mình vẫn bắt DataIntegrityViolationException sau saveAndFlush phòng trường hợp hai request đổi trùng email cùng lúc.
    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest request, String clientIp) {
        User user = authService.requireCurrentUser();
        enforceRateLimit(clientIp, "profile:" + user.getId());

        UpdateProfileRequest payload = request == null ? new UpdateProfileRequest(null, null) : request;
        String email = User.validateEmail(payload.rawEmail());
        String resolvedFullName = User.resolveFullName(payload.fullName(), user.getUsername());
        if (userRepository.existsByEmailAndIdNot(email, user.getId())) {
            throw new InvalidRequestException(AuthService.EMAIL_IN_USE_MESSAGE);
        }

        user.updateProfile(resolvedFullName, email, LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));

        try {
            User saved = userRepository.saveAndFlush(user);
            return UserProfileResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new InvalidRequestException(AuthService.EMAIL_IN_USE_MESSAGE);
        }
    }

    // Xác minh mật khẩu cũ, áp chính sách mật khẩu mới, tăng tokenVersion để cắt hết phiên cũ rồi cấp JWT mới.
    @Transactional
    public AuthResponse changePassword(ChangePasswordRequest request, String clientIp) {
        User user = authService.requireCurrentUser();
        enforceRateLimit(clientIp, "password:" + user.getId());

        ChangePasswordRequest payload = request == null ? new ChangePasswordRequest(null, null) : request;
        String currentPassword = payload.rawCurrentPassword();
        String newPassword = payload.rawNewPassword();

        // Chặn mật khẩu vượt quá giới hạn byte của BCrypt trước khi gọi matches để tránh BCrypt cắt bớt chuỗi.
        if (currentPassword.isEmpty()
                || PasswordPolicy.DEFAULT.exceedsMaxBytes(currentPassword)
                || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new InvalidRequestException("Mật khẩu hiện tại chưa đúng.");
        }
        // Việt giữ tách hai nhánh này nhé, tài khoản admin luôn phải đạt chuẩn mạnh hơn người dùng thường.
        if (user.isAdmin()) {
            PasswordPolicy.DEFAULT.validateAdminPassword(newPassword);
        } else {
            PasswordPolicy.DEFAULT.validateUserPassword(newPassword);
        }
        if (newPassword.equals(currentPassword)) {
            throw new InvalidRequestException("Mật khẩu mới cần khác mật khẩu hiện tại.");
        }

        // user.changePassword tự tăng tokenVersion bên trong entity nên mọi token cũ sẽ mất hiệu lực sau khi flush.
        user.changePassword(passwordEncoder.encode(newPassword), LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
        User saved = userRepository.saveAndFlush(user);

        // Chỉ tài khoản admin tự đổi mật khẩu mới cần ghi vết vào bảng audit.
        if (saved.isAdmin()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("username", saved.getUsername());
            details.put("selfInitiated", true);
            auditTrailWriter.recordForActor(
                    saved.getId(),
                    saved.getUsername(),
                    ACTION_ADMIN_PASSWORD_CHANGED,
                    TARGET_TYPE_USER,
                    String.valueOf(saved.getId()),
                    details
            );
        }

        String token = jwtService.issueLoginToken(saved.getUsername(), saved.getTokenVersion());
        return AuthResponse.of(saved, token);
    }

    private void enforceRateLimit(String clientIp, String scopeKey) {
        long retryAfter = loginRateLimiter.retryAfterSeconds(clientIp, scopeKey);
        if (retryAfter > 0) {
            throw new TooManyRequestsException(RATE_LIMIT_MESSAGE, retryAfter);
        }
    }
}
