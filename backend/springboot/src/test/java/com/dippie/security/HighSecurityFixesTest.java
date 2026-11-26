package com.dippie.security;

import com.dippie.model.movie.Movie;
import com.dippie.model.user.Role;
import com.dippie.model.user.User;
import com.dippie.repository.movie.MovieRepository;
import com.dippie.repository.user.UserRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// test hồi quy security header, proxy tin cậy, luật mật khẩu, MySQL TLS, Swagger, Actuator với giờ UTC
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class HighSecurityFixesTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final MovieRepository movieRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final ApplicationEventPublisher eventPublisher;

    HighSecurityFixesTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            MovieRepository movieRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginRateLimiter loginRateLimiter,
            ClientIpResolver clientIpResolver,
            ApplicationEventPublisher eventPublisher
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.movieRepository = movieRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginRateLimiter = loginRateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.eventPublisher = eventPublisher;
    }

    @BeforeEach
    void setUp() {
        loginRateLimiter.reset();
        movieRepository.deleteAll();
        userRepository.deleteAll();
    }

    // check Spring gắn đủ CSP, Referrer-Policy, Permissions-Policy, X-Frame-Options, nosniff với no-store cho API cần login
    @Test
    void setsSpringSecurityHeadersAndNoStoreOnAuthenticatedApiResponses() throws Exception {
        User alice = userRepository.saveAndFlush(new User(
                "alice_hdr", "alice.hdr@example.com", passwordEncoder.encode("secret123"), "Alice",
                Role.ROLE_USER, LocalDateTime.of(2026, 3, 1, 10, 0)
        ));
        String token = jwtService.issueLoginToken(alice.getUsername(), alice.getTokenVersion());

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.timestamp", endsWith("Z")));
    }

    // chỉ tin X-Forwarded-For từ proxy nội bộ trong dải cho phép, đồng thời siết số lần thử đổi mật khẩu theo nick
    @Test
    void resolvesClientIpOnlyFromTrustedProxyAndThrottlesPasswordChange() throws Exception {
        MockHttpServletRequest untrusted = new MockHttpServletRequest();
        untrusted.setRemoteAddr("203.0.113.50");
        untrusted.addHeader("X-Forwarded-For", "198.51.100.99");
        assertThat(clientIpResolver.resolve(untrusted)).isEqualTo("203.0.113.50");

        MockHttpServletRequest trusted = new MockHttpServletRequest();
        trusted.setRemoteAddr("172.18.0.5");
        trusted.addHeader("X-Forwarded-For", "1.2.3.4, 198.51.100.77, 10.0.0.2");
        assertThat(clientIpResolver.resolve(trusted)).isEqualTo("198.51.100.77");

        // nhập sai 10 lần ở /api/users/me/password thì phát 11 dính 429 kèm Retry-After
        User bob = userRepository.saveAndFlush(new User(
                "bob_pw", "bob.pw@example.com", passwordEncoder.encode("secret123"), "Bob",
                Role.ROLE_USER, LocalDateTime.of(2026, 3, 1, 10, 0)
        ));
        String bearer = "Bearer " + jwtService.issueLoginToken(bob.getUsername(), bob.getTokenVersion());
        String badBody = objectMapper.writeValueAsString(Map.of(
                "currentPassword", "wrongpass",
                "newPassword", "newpass123"
        ));
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/users/me/password")
                            .header(HttpHeaders.AUTHORIZATION, bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badBody))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    // chặn mật khẩu toàn dấu cách, email sai format và cấm admin hạ mật khẩu xuống dưới 16 ký tự
    @Test
    void rejectsWhitespacePasswordsFakeEmailsAndAdminPasswordDowngrade() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "blankpw",
                                "email", "blankpw@example.com",
                                "password", "      ",
                                "acceptedTerms", true
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PasswordPolicy.BLANK_PASSWORD_MESSAGE));

        for (String badEmail : List.of("@", "a@b", "user@.com", "user@domain..com", "a b@example.com", ".a@example.com")) {
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "username", "badmail",
                                    "email", badEmail,
                                    "password", "secret123",
                                    "acceptedTerms", true
                            ))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Địa chỉ email chưa đúng định dạng."));
        }

        // admin đừng hòng hạ mật khẩu xuống dưới 16 ký tự qua /api/users/me/password
        User admin = userRepository.saveAndFlush(new User(
                "admin_strong", "admin.strong@dippie.com",
                passwordEncoder.encode("Current-Strong-Admin-Password-1"),
                "Admin", Role.ROLE_ADMIN, LocalDateTime.of(2026, 3, 1, 10, 0)
        ));
        String adminBearer = "Bearer " + jwtService.issueLoginToken(admin.getUsername(), admin.getTokenVersion());
        mockMvc.perform(post("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "Current-Strong-Admin-Password-1",
                                "newPassword", "123456"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mật khẩu quản trị cần từ 16 ký tự đến tối đa 72 byte."));
    }

    // check tắt Swagger ngoài demo, bật TLS MySQL ở prod và Actuator chỉ mở health với info
    @Test
    void verifiesProdMysqlTlsSwaggerDisabledOutsideDemoAndActuatorConfig() throws Exception {
        // chạy profile test không phải demo nên Swagger UI với /v3/api-docs phải đóng cửa
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());

        // bắn sự kiện sẵn sàng trong MockMvc trước rồi mới gọi probe liveness, readiness của Actuator
        AvailabilityChangeEvent.publish(eventPublisher, this, LivenessState.CORRECT);
        AvailabilityChangeEvent.publish(eventPublisher, this, ReadinessState.ACCEPTING_TRAFFIC);

        // Actuator chỉ mở health với info thôi, đừng lộ chi tiết components
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
        // endpoint Actuator ngoài danh sách mở thì khách ăn 401, admin đăng nhập rồi cũng ăn 404
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
        User adminCheck = userRepository.saveAndFlush(new User(
                "actuator_admin", "actuator.admin@dippie.com", passwordEncoder.encode("StrongAdminPass123!"), "Admin",
                Role.ROLE_ADMIN, LocalDateTime.of(2026, 3, 1, 10, 0)
        ));
        String adminCheckBearer = "Bearer " + jwtService.issueLoginToken(adminCheck.getUsername(), adminCheck.getTokenVersion());
        mockMvc.perform(get("/actuator/env").header(HttpHeaders.AUTHORIZATION, adminCheckBearer))
                .andExpect(status().isNotFound());

        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> mainDocs = loader.load("application", new ClassPathResource("application.yml"));
        PropertySource<?> base = mainDocs.getFirst();
        PropertySource<?> demoDoc = mainDocs.stream()
                .filter(source -> "demo".equals(source.getProperty("spring.config.activate.on-profile")))
                .findFirst().orElseThrow();
        PropertySource<?> mysqlDoc = mainDocs.stream()
                .filter(source -> "mysql".equals(source.getProperty("spring.config.activate.on-profile")))
                .findFirst().orElseThrow();
        PropertySource<?> prod = loader.load("prod", new ClassPathResource("application-prod.yml")).getFirst();

        // Swagger tắt ở cấu hình gốc lẫn prod, mở mỗi bên demo thôi
        assertThat(base.getProperty("springdoc.api-docs.enabled")).isEqualTo(false);
        assertThat(base.getProperty("springdoc.swagger-ui.enabled")).isEqualTo(false);
        assertThat(demoDoc.getProperty("springdoc.api-docs.enabled")).isEqualTo(true);
        assertThat(prod.getProperty("springdoc.api-docs.enabled")).isEqualTo(false);

        // lên prod mặc định ép sslMode=REQUIRED với khóa allowPublicKeyRetrieval luôn
        String prodUrl = String.valueOf(prod.getProperty("spring.datasource.url"));
        assertThat(prodUrl).contains("sslMode=${MYSQL_SSL_MODE:REQUIRED}");
        assertThat(prodUrl).contains("allowPublicKeyRetrieval=${MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL:false}");
        assertThat(prodUrl).doesNotContain("useSSL=false");
        assertThat(String.valueOf(mysqlDoc.getProperty("spring.datasource.url")))
                .contains("sslMode=${MYSQL_SSL_MODE:PREFERRED}");

        // prod đẩy Actuator ra cổng 9090, mở mỗi health/info rồi xuất log JSON chuẩn ECS
        assertThat(String.valueOf(prod.getProperty("management.server.port"))).contains("9090");
        assertThat(prod.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,info");
        assertThat(prod.getProperty("logging.structured.format.console")).isEqualTo("ecs");
    }

    // giờ giấc API phải có đuôi Z chuẩn UTC, còn /api/movies mặc định giới hạn tối đa 200 phim
    @Test
    void returnsUtcIsoTimestampsWithZAndCapsMovieListing() throws Exception {
        movieRepository.saveAllAndFlush(IntStream.range(0, 210)
                .mapToObj(i -> new Movie("mov-" + i, "Movie " + i, 2025, 8.0, "20m", "Animation", null, null, null, null))
                .toList());

        MvcResult result = mockMvc.perform(get("/api/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(200))
                .andExpect(jsonPath("$.timestamp", endsWith("Z")))
                .andReturn();

        String timestamp = objectMapper.readTree(result.getResponse().getContentAsString()).path("timestamp").asText();
        assertThat(Instant.parse(timestamp)).isNotNull();

        mockMvc.perform(get("/api/movies").param("limit", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(15));
    }
}
