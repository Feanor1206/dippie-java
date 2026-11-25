package com.dippie.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippie.security.JwtService;
import com.dippie.security.LoginRateLimiter;
import com.dippie.security.Role;
import com.dippie.user.User;
import com.dippie.user.UserRepository;
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

// Kịch bản đăng xuất qua MockMvc để chắc chắn mọi thiết bị của cùng một tài khoản đều bị thu hồi JWT.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SessionLogoutControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;

    SessionLogoutControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginRateLimiter loginRateLimiter
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @BeforeEach
    void setUp() {
        loginRateLimiter.reset();
        userRepository.deleteAll();
    }

    // Gọi đăng xuất khi chưa có token hợp lệ phải nhận 401.
    @Test
    void rejectsAnonymousLogoutWith401() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập để tiếp tục."));
    }

    // Đăng xuất trên một thiết bị sẽ tăng tokenVersion làm chết hết token cũ của user đó, nhưng phiên người khác vẫn chạy bình thường.
    @Test
    void incrementsTokenVersionRevokesAllPriorTokensAndKeepsOtherUsersValid() throws Exception {
        User alice = userRepository.saveAndFlush(new User(
                "alice",
                "alice@example.com",
                passwordEncoder.encode("secret123"),
                "Alice",
                Role.ROLE_USER,
                LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        User bob = userRepository.saveAndFlush(new User(
                "bob",
                "bob@example.com",
                passwordEncoder.encode("secret123"),
                "Bob",
                Role.ROLE_USER,
                LocalDateTime.of(2026, 1, 1, 0, 0)
        ));

        String aliceDeviceOne = jwtService.issueLoginToken(alice.getUsername(), alice.getTokenVersion());
        String aliceDeviceTwo = jwtService.issueLoginToken(alice.getUsername(), alice.getTokenVersion());
        String bobToken = jwtService.issueLoginToken(bob.getUsername(), bob.getTokenVersion());

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceDeviceOne))
                .andExpect(status().isNoContent());

        User updatedAlice = userRepository.findById(alice.getId()).orElseThrow();
        assertThat(updatedAlice.getTokenVersion()).isEqualTo(1L);

        // Cả hai máy của Alice cùng mất phiên ngay lập tức.
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceDeviceOne))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceDeviceTwo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceDeviceOne))
                .andExpect(status().isUnauthorized());

        // Token của Bob không bị liên lụy.
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("bob"));

        // Alice đăng nhập lại thì nhận JWT mang tokenVersion mới và vào lại được.
        MvcResult relogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "usernameOrEmail", "alice",
                                "password", "secret123"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        String freshToken = objectMapper.readTree(relogin.getResponse().getContentAsString())
                .path("data").path("token").asText();

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + freshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice"));
    }
}
