package com.dippie.user;

import com.dippie.common.error.ForbiddenException;
import com.dippie.common.error.InvalidRequestException;
import com.dippie.common.error.TooManyRequestsException;
import com.dippie.common.error.UnauthorizedException;
import com.dippie.security.CurrentViewer;
import com.dippie.security.JwtService;
import com.dippie.security.LoginRateLimiter;
import com.dippie.security.PasswordPolicy;
import com.dippie.security.Role;
import com.dippie.security.Viewer;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 50;
    private static final int MAX_EMAIL_LENGTH = 100;
    private static final int MAX_FULL_NAME_LENGTH = 100;
    private static final String RATE_LIMIT_MESSAGE = "Bạn đã thử đăng nhập quá nhiều lần. Vui lòng chờ một phút.";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Sai tên đăng nhập hoặc mật khẩu";
    private static final String ACCOUNT_DISABLED_MESSAGE = "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;
    private final CurrentViewer currentViewer;
    private final Clock clock;

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
    }

    public AuthResponse login(LoginRequest request, String clientIp) {
        LoginRequest payload = request == null ? new LoginRequest(null, null, null) : request;
        String rawIdentifier = payload.rawIdentifier();
        enforceRateLimit(clientIp, rawIdentifier);

        String identifier = rawIdentifier.trim();
        String password = payload.rawPassword();
        if (identifier.isEmpty() || password.isEmpty()) {
            throw new InvalidRequestException("Tên đăng nhập và mật khẩu không được để trống");
        }
        if (PasswordPolicy.DEFAULT.exceedsMaxBytes(password)) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        User user = userRepository.findByUsernameOrEmailOrderByIdAsc(identifier, identifier).stream()
                .findFirst()
                .filter(candidate -> matchesPassword(password, candidate.getPassword()))
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE));

        if (!user.isEnabled()) {
            throw new ForbiddenException(ACCOUNT_DISABLED_MESSAGE);
        }

        String token = jwtService.issueLoginToken(user.getUsername(), user.getTokenVersion());
        return AuthResponse.of(user, token);
    }

    public AuthResponse register(RegisterRequest request, String clientIp) {
        RegisterRequest payload = request == null ? new RegisterRequest(null, null, null, null) : request;
        String rawUsername = payload.rawUsername();
        enforceRateLimit(clientIp, rawUsername);

        String username = rawUsername.trim();
        String email = payload.rawEmail().trim();
        String password = payload.rawPassword();
        String rawFullName = payload.fullName();

        if (username.length() < MIN_USERNAME_LENGTH || username.length() > MAX_USERNAME_LENGTH) {
            throw new InvalidRequestException("Tên đăng nhập phải từ 3 đến 50 ký tự");
        }
        if (!email.contains("@") || email.length() > MAX_EMAIL_LENGTH) {
            throw new InvalidRequestException("Định dạng email không hợp lệ");
        }
        if (rawFullName != null && rawFullName.length() > MAX_FULL_NAME_LENGTH) {
            throw new InvalidRequestException("Họ tên tối đa 100 ký tự");
        }
        PasswordPolicy.DEFAULT.validateForRegistration(password);

        if (userRepository.existsByUsername(username)) {
            throw new InvalidRequestException("Tên đăng nhập đã tồn tại!");
        }
        if (userRepository.existsByEmail(email)) {
            throw new InvalidRequestException("Email đã được sử dụng!");
        }

        String resolvedFullName = (rawFullName == null || rawFullName.trim().isEmpty())
                ? username
                : rawFullName.trim();
        User user = new User(
                username,
                email,
                passwordEncoder.encode(password),
                resolvedFullName,
                Role.ROLE_USER,
                LocalDateTime.now(clock)
        );
        User saved;
        try {
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new InvalidRequestException("Tên đăng nhập hoặc email đã được sử dụng.");
        }

        String token = jwtService.issueLoginToken(saved.getUsername(), saved.getTokenVersion());
        return AuthResponse.of(saved, token);
    }

    public UserProfileResponse currentUserProfile() {
        Viewer viewer = currentViewer.require();
        User user = userRepository.findById(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException("Chưa xác thực người dùng"));
        return UserProfileResponse.from(user);
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
