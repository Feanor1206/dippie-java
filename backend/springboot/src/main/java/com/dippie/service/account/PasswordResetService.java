package com.dippie.service.account;

import com.dippie.dto.account.PasswordResetConfirmRequest;
import com.dippie.dto.account.PasswordResetRequestDto;
import com.dippie.exception.InvalidRequestException;
import com.dippie.model.account.PasswordResetToken;
import com.dippie.model.mail.EmailMessage;
import com.dippie.model.user.User;
import com.dippie.repository.account.PasswordResetTokenRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.PasswordPolicy;
import com.dippie.service.admin.AuditTrailWriter;
import com.dippie.service.mail.MailDeliveryStrategy;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

// lo luồng quên mật khẩu, cấp token dùng 1 lần băm SHA-256, gửi mail qua Strategy rồi đá hết phiên cũ
@Service
public class PasswordResetService {

    public static final Duration TOKEN_VALIDITY = Duration.ofMinutes(30);
    public static final String REQUEST_ACCEPTED_MESSAGE =
            "Nếu email này đã đăng ký trên Dippie, bạn sẽ sớm nhận được hướng dẫn đặt lại mật khẩu.";
    public static final String INVALID_OR_EXPIRED_TOKEN_MESSAGE =
            "Đường dẫn đặt lại mật khẩu không đúng hoặc đã hết hạn. Bạn vui lòng yêu cầu gửi lại nhé.";
    public static final String ACTION_PASSWORD_RESET_COMPLETED = "PASSWORD_RESET_COMPLETED";


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

    // email chưa đăng ký hay bị khóa thì im lặng thoát luôn kẻo lộ tài khoản có tồn tại không
    @Transactional
    public void requestReset(PasswordResetRequestDto request, String clientIp) {
        rateLimiter.enforce(clientIp);

        PasswordResetRequestDto payload = request == null ? new PasswordResetRequestDto(null) : request;
        String email = User.validateEmail(payload.rawEmail());

        // tìm user đang bật theo email không phân biệt hoa thường, không thấy thì dừng để vẫn trả 202 chung
        User user = userRepository.findByEmailIgnoreCaseOrderByIdAsc(email).stream()
                .filter(User::isEnabled)
                .findFirst()
                .orElse(null);
        if (user == null) {
            return;
        }

        // sinh token ngẫu nhiên, lưu bản băm SHA-256 hạn 30 phút rồi gửi token gốc qua Strategy mail
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

    // duyệt token dùng 1 lần, đổi mật khẩu, tăng token_version cắt hết JWT cũ rồi ghi audit
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

        // khóa dòng token theo mã băm kẻo 2 request gọi cùng lúc xài trùng 1 token
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        String tokenHash = tokenCodec.hash(rawToken);
        PasswordResetToken resetToken = tokenRepository.findByTokenHashForUpdate(tokenHash)
                .filter(candidate -> !candidate.isUsed() && !candidate.isExpiredAt(now))
                .orElseThrow(() -> new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN_MESSAGE));

        User user = userRepository.findByIdForUpdate(resetToken.getUserId())
                .filter(User::isEnabled)
                .orElseThrow(() -> new InvalidRequestException(INVALID_OR_EXPIRED_TOKEN_MESSAGE));
        // Việt nhớ giữ chỗ này nhé, admin vẫn phải qua luật mật khẩu quản trị kẻo lách qua quên mật khẩu đặt pass yếu
        if (user.isAdmin()) {
            PasswordPolicy.DEFAULT.validateAdminPassword(newPassword);
        }

        // đánh dấu token đã dùng, hủy hết mấy token còn mở khác rồi đổi pass (changePassword tự tăng token_version)
        resetToken.markUsed(now);
        tokenRepository.saveAndFlush(resetToken);
        tokenRepository.invalidateOtherActiveTokens(user.getId(), resetToken.getId(), now);

        user.changePassword(passwordEncoder.encode(newPassword), now);
        User savedUser = userRepository.saveAndFlush(user);

        // ghi vết vào audit lúc đặt lại mật khẩu xong
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("username", savedUser.getUsername());
        details.put("tokenId", resetToken.getId());
        auditTrailWriter.recordForActor(
                savedUser.getId(),
                savedUser.getUsername(),
                ACTION_PASSWORD_RESET_COMPLETED,
                "USER",
                String.valueOf(savedUser.getId()),
                details
        );
    }
}
