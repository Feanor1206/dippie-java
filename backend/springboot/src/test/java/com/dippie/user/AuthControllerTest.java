package com.dippie.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippie.security.JwtService;
import com.dippie.security.LoginRateLimiter;
import com.dippie.security.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuthControllerTest {

    private static final String LEGACY_HEX_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;

    AuthControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            JwtService jwtService,
            LoginRateLimiter loginRateLimiter
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @BeforeEach
    void setUp() {
        loginRateLimiter.reset();
        userRepository.deleteAll();
    }

    @Test
    void registersUserPreservingRawPasswordAndAllowsLoginByUsernameAndEmail() throws Exception {
        Map<String, Object> registerPayload = Map.of(
                "username", "viewer",
                "email", "viewer@example.com",
                "password", " abcdef ",
                "fullName", "Viewer One"
        );

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerPayload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đăng ký tài khoản thành công"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.type").value("Bearer"))
                .andExpect(jsonPath("$.data.username").value("viewer"))
                .andExpect(jsonPath("$.data.email").value("viewer@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("Viewer One"))
                .andExpect(jsonPath("$.data.role").value("ROLE_USER"))
                .andReturn();

        String token = extractToken(registerResult);

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("viewer"))
                .andExpect(jsonPath("$.data.email").value("viewer@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("Viewer One"))
                .andExpect(jsonPath("$.data.role").value("ROLE_USER"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "usernameOrEmail", "viewer",
                                "password", " abcdef "
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đăng nhập thành công"))
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "usernameOrEmail", "viewer@example.com",
                                "password", " abcdef "
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("viewer"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "viewer",
                                "password", " abcdef "
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("viewer"));
    }

    @Test
    void rejectsDuplicateUsernameAndEmail() throws Exception {
        registerUser("alice", "alice@example.com", "secret123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice",
                                "email", "other@example.com",
                                "password", "secret123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Tên đăng nhập đã tồn tại!"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "bob",
                                "email", "alice@example.com",
                                "password", "secret123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email đã được sử dụng!"));
    }

    @Test
    void rejectsInvalidRegistrationBoundaries() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "ab",
                                "email", "ab@example.com",
                                "password", "secret123"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Tên đăng nhập phải từ 3 đến 50 ký tự"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "shortpw",
                                "email", "short@example.com",
                                "password", "12345"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu phải có ít nhất 6 ký tự"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "longpw",
                                "email", "longpw@example.com",
                                "password", "ế".repeat(25)
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu được dùng tối đa 72 byte UTF-8."));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "longmail",
                                "email", "x".repeat(101) + "@example.com",
                                "password", "secret123"
                        ))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "longname",
                                "email", "longname@example.com",
                                "password", "secret123",
                                "fullName", "x".repeat(101)
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsWrongPasswordAndOverlongPasswordOnLoginWith401() throws Exception {
        registerUser("alice", "alice@example.com", "secret123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "usernameOrEmail", "alice",
                                "password", "wrongpass"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Sai tên đăng nhập hoặc mật khẩu"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "usernameOrEmail", "alice",
                                "password", "ế".repeat(25)
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sai tên đăng nhập hoặc mật khẩu"));
    }

    @Test
    void rejectsUnauthenticatedForgedPlaybackGrantAndDeletedUserOnMe() throws Exception {
        String token = registerUser("alice", "alice@example.com", "secret123");

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Chưa xác thực người dùng"));

        String playbackGrant = jwtService.issuePlaybackGrant("asset-1", "alice", Role.ROLE_USER);
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + playbackGrant))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Chưa xác thực người dùng"));

        String forged = Jwts.builder()
                .subject("alice")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(LEGACY_HEX_SECRET)), Jwts.SIG.HS256)
                .compact();
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Chưa xác thực người dùng"));

        userRepository.deleteAll();
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Chưa xác thực người dùng"));
    }

    @Test
    void enforcesRateLimitWithRetryAfterWhileHealthStaysAvailable() throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        MvcResult limited = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Bạn đã thử đăng nhập quá nhiều lần. Vui lòng chờ một phút."))
                .andReturn();

        int retryAfter = Integer.parseInt(limited.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
        assertThat(retryAfter).isPositive();

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.service").value("Dippie MovieRCM Backend"))
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.message").value("Dịch vụ đang hoạt động."));
    }

    @Test
    void concurrentDuplicateRegistrationReturnsOne201AndOne400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "parallel",
                "email", "parallel@example.com",
                "password", "abcdef"
        ));
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> call = () -> mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn()
                    .getResponse()
                    .getStatus();
            List<Future<Integer>> futures = executor.invokeAll(List.of(call, call));
            List<Integer> statuses = List.of(futures.get(0).get(), futures.get(1).get())
                    .stream()
                    .sorted()
                    .toList();
            assertThat(statuses).containsExactly(201, 400);
        }
    }

    private String registerUser(String username, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "password", password
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
