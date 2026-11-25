package com.dippie.passwordreset;

import com.dippie.admin.AuditTrailWriter;
import com.dippie.common.error.InvalidRequestException;
import com.dippie.mail.EmailMessage;
import com.dippie.mail.MailDeliveryStrategy;
import com.dippie.security.PasswordPolicy;
import com.dippie.user.User;
import com.dippie.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Điều phối luồng quên mật khẩu: cấp token dùng một lần dạng băm SHA-256, gửi mail qua Strategy và thu hồi mọi phiên cũ sau khi đổi mật khẩu.
@Service
public class PasswordResetService {

    public static final Duration TOKEN_VALIDITY = Duration.ofMinutes(30);
    public static final String REQUEST_ACCEPTED_MESSAGE =
            "Nếu email này đã đăng ký trên Dippie, bạn sẽ sớm nhận được hướng dẫn đặt lại mật khẩu.";
    public static final String INVALID_OR_EXPIRED_TOKEN_MESSAGE =
            "Đường dẫn đặt lại mật khẩu không đúng hoặc đã hết hạn. Bạn vui lòng yêu cầu gửi lại nhé.";
    public static final String ACTION_PASSWORD_RESET_COMPLETED = "PASSWORD_RESET_COMPLETED";

    private static final String TARGET_TYPE_USER = "USER";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final ResetTokenCodec tokenCodec;
    private final PasswordResetEmailTemplate emailTemplate;
    private final MailDeliveryStrategy mailDeliveryStrategy;
    private final PasswordResetRateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final AuditTrailWriter auditTrailWriter;
    private final Clock clock;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            ResetTokenCodec tokenCodec,
            PasswordResetEmailTemplate emailTemplate,
            MailDeliveryStrategy mailDeliveryStrategy,
            PasswordResetRateLimiter rateLimiter,
            PasswordEncoder passwordEncoder,
            AuditTrailWriter auditTrailWriter,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.tokenCodec = tokenCodec;
        this.emailTemplate = emailTemplate;
        this.mailDeliveryStrategy = mailDeliveryStrategy;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.auditTrailWriter = auditTrailWriter;
        this.clock = clock;
    }

    // Xử lý yêu cầu quên mật khẩu; nếu email chưa đăng ký hoặc tài khoản đang bị khóa thì âm thầm kết thúc để không lộ thông tin tài khoản.
    @Transactional
    public void requestReset(PasswordResetRequestDto request, String clientIp) {
        rateLimiter.enforce(clientIp);

        PasswordResetRequestDto payload = request == null ? new PasswordResetRequestDto(null) : request;
        String email = User.validateEmail(payload.rawEmail());

        // Tìm tài khoản đang hoạt động theo email không phân biệt hoa thường; không thấy thì dừng im lặng để giữ đúng phản hồi 202 chung.
        User user = userRepository.findByEmailIgnoreCaseOrderByIdAsc(email).stream()
                .filter(User::isEnabled)
                .findFirst()
                .orElse(null);
        if (user == null) {
            return;
        }

        // Sinh token ngẫu nhiên, chỉ lưu mã băm SHA-256 có hạn 30 phút rồi gửi token gốc qua Strategy mail.
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        String rawToken = tokenCodec.generateRawToken();
        String tokenHash = tokenCodec.hash(rawToken);
        PasswordResetToken resetToken = new PasswordResetToken(
                user.getId(),
                tokenHash,
                now.plus(TOKEN_VALIDITY),
                now
        );
        tokenRepository.saveAndFlush(resetToken);

        EmailMessage message = emailTemplate.build(user.getEmail(), user.getFullName(), rawToken);
        mailDeliveryStrategy.deliver(message);
    }

    // Xác nhận token một lần, đổi mật khẩu, tăng token_version để vô hiệu hóa các JWT cũ và ghi nhật ký.
    @Transactional
    public void confirmReset(PasswordResetConfirmRequest request) {
        PasswordResetConfirmRequest payload = request == null
                ? new PasswordResetConfirmRequest(null, null)
                : request;
        String rawToken = payload.rawToken().trim();
        if (rawToken.isEmpty()) {
            throw new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN_MESSAGE);
        }
        String newPassword = payload.rawNewPassword();
        PasswordPolicy.DEFAULT.validateUserPassword(newPassword);

        // Khóa dòng token theo mã băm để hai request xác nhận cùng lúc không tiêu thụ trùng một token.
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        String tokenHash = tokenCodec.hash(rawToken);
        PasswordResetToken resetToken = tokenRepository.findByTokenHashForUpdate(tokenHash)
                .filter(candidate -> !candidate.isUsed() && !candidate.isExpiredAt(now))
                .orElseThrow(() -> new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN_MESSAGE));

        User user = userRepository.findByIdForUpdate(resetToken.getUserId())
                .filter(User::isEnabled)
                .orElseThrow(() -> new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN_MESSAGE));
        // Việt nhớ giữ nhánh kiểm tra này nhé: admin cũng phải qua luật mật khẩu quản trị, tránh lách qua luồng quên mật khẩu để đặt mật khẩu yếu.
        if (user.isAdmin()) {
            PasswordPolicy.DEFAULT.validateAdminPassword(newPassword);
        }

        // Đánh dấu token đã dùng, khóa hết các token còn mở khác của tài khoản này rồi đổi mật khẩu (hàm changePassword sẽ tự tăng token_version).
        resetToken.markUsed(now);
        tokenRepository.saveAndFlush(resetToken);
        tokenRepository.invalidateOtherActiveTokens(user.getId(), resetToken.getId(), now);

        user.changePassword(passwordEncoder.encode(newPassword), now);
        User savedUser = userRepository.saveAndFlush(user);

        // Lưu vết vào nhật ký quản trị khi người dùng đặt lại mật khẩu xong.
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("username", savedUser.getUsername());
        details.put("tokenId", resetToken.getId());
        auditTrailWriter.recordForActor(
                savedUser.getId(),
                savedUser.getUsername(),
                ACTION_PASSWORD_RESET_COMPLETED,
                TARGET_TYPE_USER,
                String.valueOf(savedUser.getId()),
                details
        );
    }
}
