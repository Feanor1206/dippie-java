package com.dippie.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippie.assistant.chat.ChatStreamGuard;
import com.dippie.user.AdminBootstrap;
import com.dippie.user.BootstrapAdminProperties;
import com.dippie.user.DemoAccountSeeder;
import com.dippie.user.User;
import com.dippie.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// Bộ test hồi quy cho mấy lỗi bảo mật nặng: không bật profile demo mặc định, giới hạn tần suất và luồng SSE ở /api/chat, và chặn body JSON quá cỡ.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CriticalSecurityFixesTest {

    private final MockMvc mockMvc;
    private final ChatStreamGuard chatStreamGuard;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    private final ThreadPoolTaskExecutor mvcAsyncTaskExecutor;

    CriticalSecurityFixesTest(
            MockMvc mockMvc,
            ChatStreamGuard chatStreamGuard,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            Clock clock,
            @Qualifier("mvcAsyncTaskExecutor") ThreadPoolTaskExecutor mvcAsyncTaskExecutor
    ) {
        this.mockMvc = mockMvc;
        this.chatStreamGuard = chatStreamGuard;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.mvcAsyncTaskExecutor = mvcAsyncTaskExecutor;
    }

    @BeforeEach
    void setUp() {
        chatStreamGuard.resetRateLimit();
        userRepository.deleteAll();
    }

    // Kiểm tra application.yml không tự bật demo hay seed dữ liệu mẫu, đồng thời vẫn chặn mật khẩu admin yếu khi chạy prod.
    @Test
    void disablesDefaultDemoProfileAndChecksWeakAdminPasswordsEvenIfSeedFlagSetInProd() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> docs = loader.load("application", new ClassPathResource("application.yml"));
        PropertySource<?> base = docs.getFirst();
        assertThat(base.getProperty("spring.profiles.default")).isNull();
        assertThat(base.getProperty("dippie.seed-demo-data")).isEqualTo(false);

        // Không được sinh khóa JWT tạm khi vừa bật demo vừa bật prod hoặc mysql.
        MockEnvironment mixedProdEnv = new MockEnvironment().withProperty("spring.profiles.active", "demo,prod");
        mixedProdEnv.setActiveProfiles("demo", "prod");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new JwtService(
                new JwtProperties("", java.time.Duration.ofHours(24), java.time.Duration.ofMinutes(5)),
                mixedProdEnv,
                clock
        )).isInstanceOf(IllegalStateException.class);

        // Tài khoản admin còn dùng mật khẩu yếu "password" phải bị AdminBootstrap chặn lúc khởi động ở prod.
        userRepository.saveAndFlush(new User(
                "alex", "alex@dippie.com", passwordEncoder.encode("password"), "Alex",
                Role.ROLE_ADMIN, LocalDateTime.now(clock)
        ));
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        AdminBootstrap runner = new AdminBootstrap(
                new BootstrapAdminProperties("", "", ""),
                true,
                prodEnv,
                userRepository,
                passwordEncoder,
                null,
                clock
        );
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
    }

    // Kiểm tra pool async có giới hạn, giới hạn số request mỗi phút và số luồng SSE đồng thời trên /api/chat.
    @Test
    void enforcesChatRateLimitConcurrencyCapAndBoundedAsyncExecutor() throws Exception {
        assertThat(mvcAsyncTaskExecutor.getMaxPoolSize()).isGreaterThan(0).isLessThanOrEqualTo(64);
        assertThat(mvcAsyncTaskExecutor.getQueueCapacity()).isGreaterThan(0);

        // Gửi quá 20 lần trong một phút từ cùng IP phải nhận 429 kèm detail tiếng Việt trước khi mở stream.
        for (int i = 0; i < 20; i++) {
            MvcResult started = mockMvc.perform(post("/api/chat")
                            .with(request -> {
                                request.setRemoteAddr("198.51.100.10");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"query\":\"Xin chào!\"}"))
                    .andExpect(request().asyncStarted())
                    .andReturn();
            mockMvc.perform(asyncDispatch(started)).andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/chat")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.10");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Xin chào!\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.detail").value(ChatStreamGuard.RATE_LIMIT_MESSAGE));

        // Chiếm hết slot đồng thời của ChatStreamGuard để chắc chắn request kế tiếp nhận 429 ngay.
        chatStreamGuard.resetRateLimit();
        List<ChatStreamGuard.Permit> held = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < chatStreamGuard.maxConcurrentStreams(); i++) {
                held.add(chatStreamGuard.acquire("203.0.113." + (10 + i), null));
            }
            mockMvc.perform(post("/api/chat")
                            .with(request -> {
                                request.setRemoteAddr("198.51.100.25");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"query\":\"Xin chào!\"}"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                    .andExpect(jsonPath("$.detail").value(ChatStreamGuard.CONCURRENCY_LIMIT_MESSAGE));
        } finally {
            for (ChatStreamGuard.Permit permit : held) {
                permit.close();
            }
        }
        assertThat(chatStreamGuard.availablePermits()).isEqualTo(chatStreamGuard.maxConcurrentStreams());
    }

    // Kiểm tra chặn body JSON quá giới hạn (413) trên /v1/search, /v1/discover, /api/auth/login và /api/admin/catalog/import, gồm cả request chunked.
    @Test
    void rejectsOversizedJsonBodiesWith413AndVietnameseMessage() throws Exception {
        byte[] oversizedJson = ("{\"query\":\"" + "a".repeat(70_000) + "\"}").getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/v1/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedJson))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.detail").value("Dữ liệu gửi lên quá lớn."));

        mockMvc.perform(post("/v1/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedJson))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.detail").value("Dữ liệu gửi lên quá lớn."));

        // Request gửi kiểu chunked không có Content-Length nhưng vượt 64 KB vẫn phải bị stream đếm byte chặn 413.
        mockMvc.perform(post("/v1/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedJson)
                        .with(request -> {
                            request.addHeader("Transfer-Encoding", "chunked");
                            return request;
                        }))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.detail").value("Dữ liệu gửi lên quá lớn."));

        // userId vượt quá 128 ký tự trên /v1/discover phải bị từ chối với mã 422.
        mockMvc.perform(post("/v1/discover")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"doraemon\",\"userId\":\"" + "u".repeat(129) + "\"}"))
                .andExpect(status().isUnprocessableEntity());

        // Body vượt ngưỡng ở /api/auth/login trả 413 theo khung ApiResponse.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedJson))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Dữ liệu gửi lên quá lớn."));

        // Admin gửi Content-Length lớn hơn 25 MB tới /api/admin/catalog/import phải bị cắt ngay với mã 413.
        userRepository.saveAndFlush(new User(
                "boss", "boss@dippie.com", passwordEncoder.encode("A-strong-private-password-123"), "Boss",
                Role.ROLE_ADMIN, LocalDateTime.now(clock)
        ));
        String adminToken = jwtService.issueLoginToken("boss");
        mockMvc.perform(post("/api/admin/catalog/import")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(30L * 1024L * 1024L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Dữ liệu gửi lên quá lớn."));
    }
}
