package com.dippie.controller.account;

import com.dippie.model.user.Role;
import com.dippie.model.user.User;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.JwtService;
import com.dippie.security.LoginRateLimiter;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// test đăng xuất qua MockMvc để chắc đá sạch JWT trên mọi thiết bị của cùng nick
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

    // chưa có token hợp lệ mà đòi logout thì trả 401
    @Test
    void rejectsAnonymousLogoutWith401() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập để tiếp tục."));
    }

    // logout 1 máy là tăng tokenVersion cho chết sạch token cũ của nick đó, máy người khác không ảnh hưởng
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

        // hai máy của Alice bay phiên cùng lúc luôn
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceDeviceOne))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceDeviceTwo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceDeviceOne))
                .andExpect(status().isUnauthorized());

        // token của Bob vẫn sống khỏe
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("bob"));

        // Alice đăng nhập lại là có JWT mang tokenVersion mới vào lại ngon lành
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
