package com.dippie.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippie.security.LoginRateLimiter;
import com.dippie.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// Xác minh đăng ký bắt buộc đồng ý điều khoản, lưu mốc terms_accepted_at và tài khoản cũ vẫn đăng nhập bình thường.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RegistrationTermsTest {

    private static final String TERMS_REQUIRED_MESSAGE =
            "Bạn cần đồng ý với Điều khoản sử dụng và Chính sách quyền riêng tư.";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter loginRateLimiter;

    RegistrationTermsTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            LoginRateLimiter loginRateLimiter
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginRateLimiter = loginRateLimiter;
    }

    @BeforeEach
    void setUp() {
        loginRateLimiter.reset();
        userRepository.deleteAll();
    }

    // Thiếu acceptedTerms hoặc gửi false đều phải trả 400 và không tạo dòng user nào trong DB.
    @Test
    void rejectsMissingOrFalseAcceptedTerms() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "no_terms",
                                "email", "noterms@example.com",
                                "password", "secret123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(TERMS_REQUIRED_MESSAGE));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "false_terms",
                                "email", "falseterms@example.com",
                                "password", "secret123",
                                "acceptedTerms", false
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(TERMS_REQUIRED_MESSAGE));

        assertThat(userRepository.count()).isZero();
    }

    // Tài khoản mới phải lưu terms_accepted_at, còn tài khoản cũ có terms_accepted_at = null vẫn đăng nhập được.
    @Test
    void storesTermsAcceptedAtOnRegistrationWithoutAffectingExistingAccounts() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "agreed_user",
                                "email", "agreed@example.com",
                                "password", "secret123",
                                "fullName", "Người xem mới",
                                "acceptedTerms", true
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("agreed_user"));

        User registered = userRepository.findByUsername("agreed_user").orElseThrow();
        assertThat(registered.getTermsAcceptedAt()).isNotNull();

        // Giả lập tài khoản cũ có terms_accepted_at để trống, kiểm tra không bị chặn lúc đăng nhập.
        User legacy = userRepository.saveAndFlush(new User(
                "legacy_user",
                "legacy@example.com",
                passwordEncoder.encode("secret123"),
                "Tài khoản cũ",
                Role.ROLE_USER,
                LocalDateTime.of(2025, 1, 1, 0, 0)
        ));
        assertThat(legacy.getTermsAcceptedAt()).isNull();

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "usernameOrEmail", "legacy_user",
                                "password", "secret123"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data").path("token").asText();
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("legacy_user"));
    }
}
