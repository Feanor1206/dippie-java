package com.dippie.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippie.catalog.domain.CatalogImportedEvent;
import com.dippie.security.JwtService;
import com.dippie.security.LoginRateLimiter;
import com.dippie.security.Role;
import com.dippie.user.User;
import com.dippie.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

// Bộ test tích hợp cho các API quản trị: kiểm tra phân quyền, phân trang tìm user, chặn hạ quyền admin cuối cùng khi chạy song song và ghi nhật ký.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AdminControllerTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final AdminAuditEventRepository auditEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    AdminControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            AdminAuditEventRepository auditEventRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginRateLimiter loginRateLimiter,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginRateLimiter = loginRateLimiter;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @BeforeEach
    void setUp() {
        loginRateLimiter.reset();
        auditEventRepository.deleteAll();
        userRepository.deleteAll();
    }

    // Chặn khách chưa đăng nhập lẫn tài khoản thường trên toàn bộ endpoint quản trị.
    @Test
    void rejectsNonAdminAndAnonymousAcrossAdminEndpoints() throws Exception {
        User regular = saveUser("viewer", "viewer@example.com", Role.ROLE_USER);
        String userToken = bearerFor(regular);

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/users/" + regular.getId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROLE_ADMIN\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/users/" + regular.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/audit-events").header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, userToken))
                .andExpect(status().isForbidden());
    }

    // Kiểm tra phân trang và tìm kiếm không phân biệt hoa thường theo username hoặc email.
    @Test
    void listsSearchesAndPagesUsers() throws Exception {
        User admin = saveUser("boss", "boss@dippie.com", Role.ROLE_ADMIN);
        saveUser("alice", "alice@studio.vn", Role.ROLE_USER);
        saveUser("bob", "bob@dippie.com", Role.ROLE_USER);
        saveUser("charlie", "charlie@other.org", Role.ROLE_USER);
        String adminAuth = bearerFor(admin);

        mockMvc.perform(get("/api/admin/users?page=0&size=2")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.hasPrevious").value(false))
                .andExpect(jsonPath("$.data.items[0].username").value("boss"))
                .andExpect(jsonPath("$.data.items[0].enabled").value(true))
                .andExpect(jsonPath("$.data.items[0].createdAt").isNotEmpty());

        mockMvc.perform(get("/api/admin/users?query=dippie&page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].username").value("boss"))
                .andExpect(jsonPath("$.data.items[1].username").value("bob"));

        mockMvc.perform(get("/api/admin/users?query=ALI&page=0&size=10")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].username").value("alice"));
    }

    // Chạy trọn vòng đổi quyền, khóa mở tài khoản, thu hồi JWT cũ và xác minh thứ tự dòng nhật ký.
    @Test
    void enforcesRoleAndStatusRulesRevokesTokensAndRecordsAuditEvents() throws Exception {
        User admin = saveUser("admin1", "admin1@dippie.com", Role.ROLE_ADMIN);
        User viewer = saveUser("viewer1", "viewer1@dippie.com", Role.ROLE_USER);
        String adminAuth = bearerFor(admin);
        String viewerToken = bearerFor(viewer);

        mockMvc.perform(patch("/api/admin/users/" + admin.getId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ROLE_USER"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(AdminUserPolicy.SELF_CHANGE_MESSAGE));

        mockMvc.perform(patch("/api/admin/users/" + admin.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(AdminUserPolicy.SELF_CHANGE_MESSAGE));

        mockMvc.perform(patch("/api/admin/users/999999/role")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ROLE_ADMIN"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/admin/users/" + viewer.getId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ROLE_INVALID"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/admin/users/" + viewer.getId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ROLE_ADMIN"));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, viewerToken))
                .andExpect(status().isUnauthorized());

        User promotedViewer = userRepository.findById(viewer.getId()).orElseThrow();
        String viewerNewAuth = bearerFor(promotedViewer);

        mockMvc.perform(patch("/api/admin/users/" + viewer.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, viewerNewAuth))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "usernameOrEmail", "viewer1",
                                "password", "secret123"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Tài khoản này đang bị khóa. Hãy liên hệ quản trị viên để được hỗ trợ."));

        mockMvc.perform(patch("/api/admin/users/" + viewer.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        eventPublisher.publishEvent(new CatalogImportedEvent(
                "pkg-001",
                2,
                5L,
                "snap-005",
                admin.getUsername(),
                LocalDateTime.now(clock)
        ));

        mockMvc.perform(get("/api/admin/audit-events?page=0&size=20")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.items[0].action").value("CATALOG_IMPORTED"))
                .andExpect(jsonPath("$.data.items[0].actorUsername").value("admin1"))
                .andExpect(jsonPath("$.data.items[0].targetType").value("CATALOG_PACKAGE"))
                .andExpect(jsonPath("$.data.items[0].targetId").value("pkg-001"))
                .andExpect(jsonPath("$.data.items[0].details.packageId").value("pkg-001"))
                .andExpect(jsonPath("$.data.items[0].details.revision").value(2))
                .andExpect(jsonPath("$.data.items[0].details.snapshotVersion").value(5))
                .andExpect(jsonPath("$.data.items[1].action").value("USER_ENABLED"))
                .andExpect(jsonPath("$.data.items[2].action").value("USER_DISABLED"))
                .andExpect(jsonPath("$.data.items[3].action").value("USER_ROLE_CHANGED"))
                .andExpect(jsonPath("$.data.items[3].details.previousRole").value("ROLE_USER"))
                .andExpect(jsonPath("$.data.items[3].details.newRole").value("ROLE_ADMIN"));
    }

    // Cho hai admin hạ quyền nhau cùng lúc để xác nhận khóa DB luôn giữ lại đúng một admin đang hoạt động.
    @Test
    void concurrentMutualAdminDemotionAlwaysLeavesAtLeastOneEnabledAdmin() throws Exception {
        User admin1 = saveUser("admin1", "admin1@dippie.com", Role.ROLE_ADMIN);
        User admin2 = saveUser("admin2", "admin2@dippie.com", Role.ROLE_ADMIN);
        String auth1 = bearerFor(admin1);
        String auth2 = bearerFor(admin2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> demoteAdmin2 = () -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(patch("/api/admin/users/" + admin2.getId() + "/role")
                                .header(HttpHeaders.AUTHORIZATION, auth1)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"ROLE_USER\"}"))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            Callable<Integer> demoteAdmin1 = () -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(patch("/api/admin/users/" + admin1.getId() + "/role")
                                .header(HttpHeaders.AUTHORIZATION, auth2)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"ROLE_USER\"}"))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };

            Future<Integer> demoteAdmin2Future = executor.submit(demoteAdmin2);
            Future<Integer> demoteAdmin1Future = executor.submit(demoteAdmin1);
            ready.await();
            start.countDown();

            List<Integer> statuses = List.of(demoteAdmin2Future.get(), demoteAdmin1Future.get())
                    .stream()
                    .sorted()
                    .toList();
            assertThat(statuses).containsExactly(200, 409);
        }

        long remainingEnabledAdmins = userRepository.findByRole(Role.ROLE_ADMIN).stream()
                .filter(User::isEnabled)
                .count();
        assertThat(remainingEnabledAdmins).isEqualTo(1L);
    }

    private User saveUser(String username, String email, Role role) {
        return userRepository.saveAndFlush(new User(
                username,
                email,
                passwordEncoder.encode("secret123"),
                username,
                role,
                LocalDateTime.now(clock)
        ));
    }

    private String bearerFor(User user) {
        return "Bearer " + jwtService.issueLoginToken(user.getUsername(), user.getTokenVersion());
    }
}
