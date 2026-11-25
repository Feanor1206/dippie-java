package com.dippie.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippie.security.LoginRateLimiter;
import com.dippie.user.User;
import com.dippie.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// Test end-to-end cho sửa hồ sơ, đổi mật khẩu kèm thu hồi JWT cũ và hành vi khi tài khoản bị khóa.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AccountControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final LoginRateLimiter loginRateLimiter;

    AccountControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            LoginRateLimiter loginRateLimiter
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.loginRateLimiter = loginRateLimiter;
    }

    @BeforeEach
    void setUp() {
        loginRateLimiter.reset();
        userRepository.deleteAll();
    }

    // Kiểm tra đổi họ tên/email, giữ nguyên email của chính mình và chặn trùng hoặc sai giới hạn dữ liệu.
    @Test
    void updatesOwnProfileAndValidatesEmailUniquenessAndBounds() throws Exception {
        String aliceToken = register("alice", "alice@example.com", "secret123", "Alice One");
        register("bob", "bob@example.com", "secret123", "Bob One");

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Unauth",
                                "email", "unauth@example.com"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập để tiếp tục."));

        mockMvc.perform(put("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Alice Updated",
                                "email", "alice.updated@example.com"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.email").value("alice.updated@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("Alice Updated"))
                .andExpect(jsonPath("$.data.role").value("ROLE_USER"));

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("alice.updated@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("Alice Updated"));

        mockMvc.perform(put("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Alice Again",
                                "email", "alice.updated@example.com"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Alice Again"));

        mockMvc.perform(put("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Alice Conflict",
                                "email", "bob@example.com"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email này đã có người dùng."));

        mockMvc.perform(put("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Alice Bad Mail",
                                "email", "not-an-email"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Địa chỉ email chưa đúng định dạng."));

        mockMvc.perform(put("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "x".repeat(101),
                                "email", "alice.updated@example.com"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Họ tên không quá 100 ký tự."));
    }

    // Đổi mật khẩu thành công phải vô hiệu hóa token cũ ngay và cấp token mới dùng được liền.
    @Test
    void changesPasswordRevokesOldTokenAndReturnsWorkingNewToken() throws Exception {
        String oldToken = register("alice", "alice@example.com", "secret123", "Alice");

        mockMvc.perform(post("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "wrongpass",
                                "newPassword", "newsecret123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu hiện tại chưa đúng."));

        mockMvc.perform(post("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "secret123",
                                "newPassword", "12345"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu cần ít nhất 6 ký tự."));

        mockMvc.perform(post("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "secret123",
                                "newPassword", "secret123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu mới cần khác mật khẩu hiện tại."));

        MvcResult changeResult = mockMvc.perform(post("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "secret123",
                                "newPassword", "newsecret123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.type").value("Bearer"))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andReturn();

        String newToken = extractToken(changeResult);

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập để tiếp tục."));

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "usernameOrEmail", "alice",
                                "password", "newsecret123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    // Tài khoản bị khóa phải rớt JWT cũ, nhận 401 khi nhập sai mật khẩu (tránh lộ trạng thái khóa) và nhận 403 khi đúng mật khẩu.
    @Test
    void disabledUserReceives403OnValidLogin401OnWrongPasswordAndExistingTokenIsRevoked() throws Exception {
        String existingToken = register("locked", "locked@example.com", "secret123", "Locked User");

        User user = userRepository.findByUsername("locked").orElseThrow();
        user.disable();
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + existingToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập để tiếp tục."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "usernameOrEmail", "locked",
                                "password", "wrongpass"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập hoặc mật khẩu chưa đúng."));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "usernameOrEmail", "locked",
                                "password", "secret123"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Tài khoản này đang bị khóa. Hãy liên hệ quản trị viên để được hỗ trợ."));
    }

    private String register(String username, String email, String password, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "password", password,
                                "fullName", fullName,
                                "acceptedTerms", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return extractToken(result);
    }

    private String extractToken(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }
}
