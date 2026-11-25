package com.dippie.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippie.admin.AdminAuditEvent;
import com.dippie.admin.AdminAuditEventRepository;
import com.dippie.security.JwtService;
import com.dippie.security.LoginRateLimiter;
import com.dippie.security.Role;
import com.dippie.user.User;
import com.dippie.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// Test tích hợp luồng đặt lại mật khẩu qua hộp thư .eml: phản hồi 202 trung lập, token dùng một lần, hết hạn, thu hồi JWT cũ và ghi nhật ký.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "dippie.mail.outbox-dir=target/dippie-mail-test",
        "dippie.mail.public-base-url=https://dippie.vn/"
})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PasswordResetControllerTest {

    private static final Path OUTBOX_DIR = Path.of("target/dippie-mail-test");
    private static final Pattern RESET_LINK_PATTERN =
            Pattern.compile("https://dippie\\.vn/dat-lai-mat-khau\\?token=([A-Za-z0-9_-]+)");

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetRateLimiter resetRateLimiter;
    private final LoginRateLimiter loginRateLimiter;
    private final AdminAuditEventRepository auditEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ResetTokenCodec tokenCodec;

    PasswordResetControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordResetRateLimiter resetRateLimiter,
            LoginRateLimiter loginRateLimiter,
            AdminAuditEventRepository auditEventRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            ResetTokenCodec tokenCodec
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.resetRateLimiter = resetRateLimiter;
        this.loginRateLimiter = loginRateLimiter;
        this.auditEventRepository = auditEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenCodec = tokenCodec;
    }

    @BeforeEach
    void setUp() throws Exception {
        resetRateLimiter.reset();
        loginRateLimiter.reset();
        tokenRepository.deleteAll();
        auditEventRepository.deleteAll();
        userRepository.deleteAll();
        cleanOutbox();
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanOutbox();
    }

    // Chạy trọn quy trình ghi tệp .eml tiếng Việt, băm token trong DB, đổi mật khẩu, vô hiệu hóa JWT cũ và chặn dùng lại token.
    @Test
    void completesEndToEndPasswordResetViaEmlOutboxRevokesSessionsAndPreventsReuse() throws Exception {
        User alice = saveUser("alice_reset", "alice.reset@example.com", true);
        String oldJwt = jwtService.issueLoginToken(alice.getUsername(), alice.getTokenVersion());

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "ALICE.RESET@example.com"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(PasswordResetService.REQUEST_ACCEPTED_MESSAGE));

        // Tệp .eml xuất ra phải mang tên <timestamp>-<recipient>.eml kèm nội dung tiếng Việt và đường dẫn đặt lại.
        List<Path> emlFiles = listOutboxFiles();
        assertThat(emlFiles).hasSize(1);
        Path emlFile = emlFiles.getFirst();
        assertThat(emlFile.getFileName().toString()).endsWith("-alice.reset@example.com.eml");

        String emlContent = Files.readString(emlFile, StandardCharsets.UTF_8);
        assertThat(emlContent).contains("Subject: Đặt lại mật khẩu tài khoản Dippie");
        assertThat(emlContent).contains("To: alice.reset@example.com");
        assertThat(emlContent).contains("30 phút");

        Matcher matcher = RESET_LINK_PATTERN.matcher(emlContent);
        assertThat(matcher.find()).isTrue();
        String rawToken = matcher.group(1);

        // Bảng token chỉ chứa mã băm SHA-256 của token, tuyệt đối không lưu nguyên bản rawToken.
        List<PasswordResetToken> storedTokens = tokenRepository.findByUserId(alice.getId());
        assertThat(storedTokens).hasSize(1);
        assertThat(storedTokens.getFirst().getTokenHash()).isEqualTo(tokenCodec.hash(rawToken));
        assertThat(storedTokens.getFirst().getTokenHash()).isNotEqualTo(rawToken);
        assertThat(storedTokens.getFirst().getExpiresAt())
                .isEqualTo(storedTokens.getFirst().getCreatedAt().plusMinutes(30));

        // Xác nhận đổi mật khẩu lần đầu trả 204.
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", rawToken,
                                "newPassword", "newpass123"
                        ))))
                .andExpect(status().isNoContent());

        // Dùng lại cùng token đó lần thứ hai phải bị từ chối với mã 400.
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", rawToken,
                                "newPassword", "another123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PasswordResetService.INVALID_OR_EXPIRED_TOKEN_MESSAGE));

        // token_version tăng nên JWT cũ mất hiệu lực; chỉ đăng nhập được bằng mật khẩu mới.
        User reloaded = userRepository.findById(alice.getId()).orElseThrow();
        assertThat(reloaded.getTokenVersion()).isEqualTo(1L);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldJwt))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "usernameOrEmail", "alice_reset",
                                "password", "secret123"
                        ))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "usernameOrEmail", "alice_reset",
                                "password", "newpass123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        // Kiểm tra dòng nhật ký PASSWORD_RESET_COMPLETED đã được lưu.
        List<AdminAuditEvent> audits = auditEventRepository.findAll();
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().getAction()).isEqualTo(PasswordResetService.ACTION_PASSWORD_RESET_COMPLETED);
        assertThat(audits.getFirst().getTargetId()).isEqualTo(String.valueOf(alice.getId()));
    }

    // Email chưa đăng ký hoặc tài khoản đang bị khóa vẫn trả đúng 202 như bình thường nhưng không tạo token hay ghi tệp .eml.
    @Test
    void returnsNeutral202WithoutSendingEmailForUnknownOrDisabledAccounts() throws Exception {
        saveUser("locked_reset", "locked.reset@example.com", false);

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "missing@example.com"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(PasswordResetService.REQUEST_ACCEPTED_MESSAGE));

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "locked.reset@example.com"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(PasswordResetService.REQUEST_ACCEPTED_MESSAGE));

        assertThat(listOutboxFiles()).isEmpty();
        assertThat(tokenRepository.count()).isZero();
    }

    // Kiểm tra các nhánh lỗi 400 gồm email sai định dạng, token hết hạn, token lạ và mật khẩu mới quá ngắn.
    @Test
    void rejectsInvalidEmailExpiredTokenUnknownTokenAndWeakNewPassword() throws Exception {
        User bob = saveUser("bob_reset", "bob.reset@example.com", true);

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Địa chỉ email chưa đúng định dạng."));

        // Tạo sẵn một token đã quá hạn từ trước để xác nhận hệ thống từ chối.
        String expiredRaw = tokenCodec.generateRawToken();
        LocalDateTime past = LocalDateTime.of(2020, 1, 1, 0, 0);
        tokenRepository.saveAndFlush(new PasswordResetToken(
                bob.getId(), tokenCodec.hash(expiredRaw), past.plusMinutes(30), past
        ));

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", expiredRaw,
                                "newPassword", "validpass123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PasswordResetService.INVALID_OR_EXPIRED_TOKEN_MESSAGE));

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", "unknown-token",
                                "newPassword", "validpass123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PasswordResetService.INVALID_OR_EXPIRED_TOKEN_MESSAGE));

        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", "any-token",
                                "newPassword", "12345"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu cần ít nhất 6 ký tự."));
    }

    // Chặn IP gửi quá 5 yêu cầu quên mật khẩu liên tiếp bằng mã 429 kèm Retry-After.
    @Test
    void enforcesRateLimitOnPasswordResetRequest() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/auth/password-reset/request")
                            .with(request -> {
                                request.setRemoteAddr("10.20.0.1");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"nobody@example.com\"}"))
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .with(request -> {
                            request.setRemoteAddr("10.20.0.1");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.message")
                        .value("Bạn vừa gửi quá nhiều yêu cầu đặt lại mật khẩu. Vui lòng đợi một lát rồi thử lại."));

        // IP khác vẫn gửi yêu cầu bình thường.
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .with(request -> {
                            request.setRemoteAddr("10.20.0.2");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isAccepted());
    }

    private User saveUser(String username, String email, boolean enabled) {
        User user = new User(
                username,
                email,
                passwordEncoder.encode("secret123"),
                username,
                Role.ROLE_USER,
                LocalDateTime.of(2026, 1, 1, 0, 0)
        );
        if (!enabled) {
            user.disable();
        }
        return userRepository.saveAndFlush(user);
    }

    private List<Path> listOutboxFiles() throws Exception {
        if (!Files.exists(OUTBOX_DIR)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(OUTBOX_DIR)) {
            return stream.filter(Files::isRegularFile).toList();
        }
    }

    private void cleanOutbox() throws Exception {
        if (!Files.exists(OUTBOX_DIR)) {
            return;
        }
        try (Stream<Path> stream = Files.list(OUTBOX_DIR)) {
            for (Path file : stream.toList()) {
                Files.deleteIfExists(file);
            }
        }
    }
}
