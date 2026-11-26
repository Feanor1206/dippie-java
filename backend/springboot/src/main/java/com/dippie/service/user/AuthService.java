package com.dippie.service.user;

import com.dippie.dto.user.AuthResponse;
import com.dippie.dto.user.LoginRequest;
import com.dippie.dto.user.RegisterRequest;
import com.dippie.dto.user.UserProfileResponse;
import com.dippie.exception.ForbiddenException;
import com.dippie.exception.InvalidRequestException;
import com.dippie.exception.TooManyRequestsException;
import com.dippie.exception.UnauthorizedException;
import com.dippie.model.user.Role;
import com.dippie.model.user.User;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.JwtService;
import com.dippie.security.LoginRateLimiter;
import com.dippie.security.PasswordPolicy;
import com.dippie.security.Viewer;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

// Facade xử lý đăng nhập, đăng ký và tra cứu tài khoản hiện tại, có giới hạn tần suất và chống dò tài khoản.
@Service
public class AuthService {

    public static final String EMAIL_IN_USE_MESSAGE = "Email này đã có người dùng.";
    public static final String DUPLICATE_ACCOUNT_MESSAGE = "Tên đăng nhập hoặc email này đã có người dùng.";
    private static final String RATE_LIMIT_MESSAGE = "Bạn đã thử quá nhiều lần. Hãy đợi một phút rồi thử lại.";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Tên đăng nhập hoặc mật khẩu chưa đúng.";
    private static final String ACCOUNT_DISABLED_MESSAGE = "Tài khoản này đang bị khóa. Hãy liên hệ quản trị viên để được hỗ trợ.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;
    private final CurrentViewer currentViewer;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginRateLimiter loginRateLimiter,
            CurrentViewer currentViewer,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginRateLimiter = loginRateLimiter;
        this.currentViewer = currentViewer;
        this.clock = clock;
        // Việt đừng xóa dòng này nhé: mình băm sẵn một chuỗi giả để khi login sai username vẫn chạy BCrypt,
        // giữ thời gian trả lỗi tương đương lúc tìm thấy user để khó dò tài khoản tồn tại.
        this.dummyPasswordHash = passwordEncoder.encode("dippie-dummy-timing-guard-2026");
    }

    // Xác thực đăng nhập bằng username hoặc email, kiểm tra trạng thái khóa sau khi khớp mật khẩu rồi phát hành JWT.
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request, String clientIp) {
        LoginRequest payload = request == null ? new LoginRequest(null, null, null) : request;
        String rawIdentifier = payload.rawIdentifier();
        enforceRateLimit(clientIp, rawIdentifier);

        String identifier = rawIdentifier.trim();
        String password = payload.rawPassword();
        if (identifier.isEmpty() || password.isEmpty()) {
            throw new InvalidRequestException("Hãy nhập đủ tên đăng nhập và mật khẩu.");
        }
        // Chặn mật khẩu quá 72 byte UTF-8 sớm để tránh BCrypt tự cắt chuỗi.
        if (PasswordPolicy.DEFAULT.exceedsMaxBytes(password)) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        // Chỉ hạ chữ thường khi chuỗi có ký tự @ (email); không tìm thấy user vẫn chạy BCrypt với dummyPasswordHash.
        String emailLookup = identifier.contains("@") ? identifier.toLowerCase(Locale.ROOT) : identifier;
        Optional<User> candidate = userRepository.findByUsernameOrEmailOrderByIdAsc(identifier, emailLookup).stream()
                .findFirst();
        if (candidate.isEmpty()) {
            passwordEncoder.matches(password, dummyPasswordHash);
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }
        User user = candidate.get();
        if (!matchesPassword(password, user.getPassword())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        // Chỉ báo tài khoản bị khóa sau khi đã đúng mật khẩu để tránh lộ trạng thái khóa cho người nhập bừa.
        if (!user.isEnabled()) {
            throw new ForbiddenException(ACCOUNT_DISABLED_MESSAGE);
        }

        String token = jwtService.issueLoginToken(user.getUsername(), user.getTokenVersion());
        return AuthResponse.of(user, token);
    }

    // Tạo tài khoản ROLE_USER mới sau khi kiểm tra đồng ý điều khoản, dữ liệu hợp lệ và chống trùng tài khoản.
    @Transactional
    public AuthResponse register(RegisterRequest request, String clientIp) {
        RegisterRequest payload = request == null ? new RegisterRequest(null, null, null, null, null) : request;
        String rawUsername = payload.rawUsername();
        enforceRateLimit(clientIp, rawUsername);

        if (!payload.isTermsAccepted()) {
            throw new InvalidRequestException("Bạn cần đồng ý với Điều khoản sử dụng và Chính sách quyền riêng tư.");
        }

        // Username bị cấm chứa @ trong User.validateUsername để không bao giờ đụng với luồng đăng nhập bằng email.
        String username = User.validateUsername(rawUsername);
        String password = payload.rawPassword();
        String email = User.validateEmail(payload.rawEmail());
        String resolvedFullName = User.resolveFullName(payload.fullName(), username);
        PasswordPolicy.DEFAULT.validateUserPassword(password);

        // Dùng chung một câu lỗi cho trùng username hoặc email để người ngoài không dò được trường nào đã tồn tại.
        if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
            throw new InvalidRequestException(DUPLICATE_ACCOUNT_MESSAGE);
        }

        // Gọi saveAndFlush rồi bắt DataIntegrityViolationException để hai request đăng ký trùng chạy cùng lúc vẫn trả 400 sạch.
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        User user = new User(
                username,
                email,
                passwordEncoder.encode(password),
                resolvedFullName,
                Role.ROLE_USER,
                now
        );
        user.acceptTerms(now);
        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new InvalidRequestException(DUPLICATE_ACCOUNT_MESSAGE);
        }

        String token = jwtService.issueLoginToken(saved.getUsername(), saved.getTokenVersion());
        return AuthResponse.of(saved, token);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse currentUserProfile() {
        return UserProfileResponse.from(requireCurrentUser());
    }

    // Nạp lại bản ghi User mới nhất từ DB theo userId trong SecurityContext.
    @Transactional(readOnly = true)
    public User requireCurrentUser() {
        Viewer viewer = currentViewer.require();
        return userRepository.findById(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException("Bạn cần đăng nhập để tiếp tục."));
    }

    private void enforceRateLimit(String clientIp, String identifier) {
        long retryAfter = loginRateLimiter.retryAfterSeconds(clientIp, identifier);
        if (retryAfter > 0) {
            throw new TooManyRequestsException(RATE_LIMIT_MESSAGE, retryAfter);
        }
    }

    private boolean matchesPassword(String rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isEmpty()) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
