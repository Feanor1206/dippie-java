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

// kiểu Facade lo đăng nhập, đăng ký với lấy user hiện tại, có rate limit và chống dò tài khoản
@Service
public class AuthService {

    public static final String EMAIL_IN_USE_MESSAGE = "Email này đã có người dùng.";
    public static final String DUPLICATE_ACCOUNT_MESSAGE = "Tên đăng nhập hoặc email này đã có người dùng.";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Tên đăng nhập hoặc mật khẩu chưa đúng.";

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
        // Việt đừng xóa dòng này nhé, băm sẵn chuỗi giả để login sai username vẫn phải chạy BCrypt
        // giữ thời gian trả lỗi ngang lúc thấy user cho khó dò xem tài khoản có tồn tại không
        this.dummyPasswordHash = passwordEncoder.encode("dippie-dummy-timing-guard-2026");
    }

    // check đăng nhập bằng username hay email, đúng mật khẩu rồi mới kiểm tra bị khóa không rồi cấp JWT
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
        // chặn pass quá 72 byte UTF-8 từ sớm kẻo BCrypt tự cắt chuỗi
        if (PasswordPolicy.DEFAULT.exceedsMaxBytes(password)) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        // chỉ hạ thường khi chuỗi có @ (email), không thấy user vẫn chạy BCrypt với dummyPasswordHash
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

        // đúng mật khẩu rồi mới báo tài khoản bị khóa kẻo lộ trạng thái khóa cho người nhập bừa
        if (!user.isEnabled()) {
            throw new ForbiddenException("Tài khoản này đang bị khóa. Hãy liên hệ quản trị viên để được hỗ trợ.");
        }

        String token = jwtService.issueLoginToken(user.getUsername(), user.getTokenVersion());
        return AuthResponse.of(user, token);
    }

    // tạo tài khoản ROLE_USER mới sau khi check đã đồng ý điều khoản, dữ liệu ổn và không trùng
    @Transactional
    public AuthResponse register(RegisterRequest request, String clientIp) {
        RegisterRequest payload = request == null ? new RegisterRequest(null, null, null, null, null) : request;
        String rawUsername = payload.rawUsername();
        enforceRateLimit(clientIp, rawUsername);

        if (!payload.isTermsAccepted()) {
            throw new InvalidRequestException("Bạn cần đồng ý với Điều khoản sử dụng và Chính sách quyền riêng tư.");
        }

        // User.validateUsername cấm dấu @ trong username để khỏi bao giờ đụng luồng login bằng email
        String username = User.validateUsername(rawUsername);
        String password = payload.rawPassword();
        String email = User.validateEmail(payload.rawEmail());
        String resolvedFullName = User.resolveFullName(payload.fullName(), username);
        PasswordPolicy.DEFAULT.validateUserPassword(password);

        // trả chung 1 câu lỗi khi trùng username hay email để khỏi ai dò được trường nào đã có
        if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
            throw new InvalidRequestException(DUPLICATE_ACCOUNT_MESSAGE);
        }

        // dùng saveAndFlush rồi bắt DataIntegrityViolationException để 2 request đăng ký trùng cùng lúc vẫn trả 400 gọn
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

    // kéo lại bản ghi User mới nhất từ DB theo userId trong SecurityContext
    @Transactional(readOnly = true)
    public User requireCurrentUser() {
        Viewer viewer = currentViewer.require();
        return userRepository.findById(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException("Bạn cần đăng nhập để tiếp tục."));
    }

    private void enforceRateLimit(String clientIp, String identifier) {
        long retryAfter = loginRateLimiter.retryAfterSeconds(clientIp, identifier);
        if (retryAfter > 0) {
            throw new TooManyRequestsException("Bạn đã thử quá nhiều lần. Hãy đợi một phút rồi thử lại.", retryAfter);
        }
    }

    private boolean matchesPassword(String rawPassword, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isEmpty()) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
