package com.dippie.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippie.security.JwtService;
import com.dippie.security.Role;
import com.dippie.user.User;
import com.dippie.user.UserRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

// Test tích hợp cho định tuyến SPA, cache file tĩnh và lỗi 503 khi chưa build frontend.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SpaControllerTest {

    @TempDir
    static Path tempDist;

    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    SpaControllerTest(MockMvc mockMvc, UserRepository userRepository, JwtService jwtService) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("dippie.web.dist", () -> tempDist.toAbsolutePath().toString());
    }

    @BeforeEach
    void prepareDistFiles() throws IOException {
        Files.createDirectories(tempDist.resolve("assets"));
        Files.createDirectories(tempDist.resolve("illustrations"));
        Files.writeString(tempDist.resolve("index.html"), "<!doctype html><title>Dippie</title>", StandardCharsets.UTF_8);
        Files.writeString(tempDist.resolve("assets/app-abc123.js"), "export const v = 1;", StandardCharsets.UTF_8);
        Files.writeString(tempDist.resolve("illustrations/cover.svg"), "<svg/>", StandardCharsets.UTF_8);
    }

    @Test
    void servesIndexHtmlWithNoCacheForAllSpaRoutesAndHeadRequests() throws Exception {
        for (String route : new String[]{"/", "/search", "/profile", "/account", "/admin/import", "/admin/users", "/admin/stats",
                "/admin/audit", "/movies/m1", "/series", "/series/s1", "/lich-su", "/admin/reports", "/dieu-khoan",
                "/quyen-rieng-tu", "/dat-lai-mat-khau", "/quen-mat-khau"}) {
            mockMvc.perform(get(route))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                    .andExpect(content().contentTypeCompatibleWith("text/html"))
                    .andExpect(content().string("<!doctype html><title>Dippie</title>"));

            mockMvc.perform(head(route))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"));
        }
    }

    @Test
    void servesStaticAssetsWithLongCacheAndIllustrations() throws Exception {
        mockMvc.perform(get("/assets/app-abc123.js"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=31536000")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")))
                .andExpect(content().string("export const v = 1;"));

        mockMvc.perform(get("/illustrations/cover.svg"))
                .andExpect(status().isOk())
                .andExpect(content().string("<svg/>"));

        mockMvc.perform(get("/assets/missing.js"))
                .andExpect(status().isNotFound());
    }

    @Test
    void servesTheSpaWith404ForUnknownBrowserRoutes() throws Exception {
        for (String route : new String[]{"/does-not-exist", "/movies", "/missing/nested/page"}) {
            mockMvc.perform(get(route).accept(MediaType.TEXT_HTML))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(content().string("<!doctype html><title>Dippie</title>"));
            mockMvc.perform(head(route).accept(MediaType.TEXT_HTML))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        }
    }

    @Test
    void keepsApiStaticAndNonHtmlMissesAsResourceErrors() throws Exception {
        // Route API không tồn tại phải bị chặn 401 ngay khi chưa đăng nhập.
        for (String route : new String[]{"/api/no-route", "/v1/no-route", "/v3/no-route"}) {
            mockMvc.perform(get(route).accept(MediaType.TEXT_HTML))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập để tiếp tục."));
        }

        // Khi đã đăng nhập, route API không tồn tại trả 404 dạng JSON thay vì index.html.
        userRepository.deleteAll();
        User viewer = userRepository.saveAndFlush(new User(
                "spa_viewer", "spa.viewer@example.com", "pw", "SPA Viewer",
                Role.ROLE_USER, LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        String bearer = "Bearer " + jwtService.issueLoginToken(viewer.getUsername(), viewer.getTokenVersion());
        for (String route : new String[]{"/api/no-route", "/v1/no-route", "/v3/no-route"}) {
            mockMvc.perform(get(route).header(HttpHeaders.AUTHORIZATION, bearer).accept(MediaType.TEXT_HTML))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Không tìm thấy nội dung này."));
        }

        for (String route : new String[]{"/assets/missing.js", "/illustrations/missing.svg"}) {
            mockMvc.perform(get(route).accept(MediaType.TEXT_HTML))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Không tìm thấy nội dung này."));
        }
        mockMvc.perform(get("/does-not-exist").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy nội dung này."));
    }

    @Test
    void returns503EnvelopeWhenIndexHtmlIsMissing() throws Exception {
        Files.deleteIfExists(tempDist.resolve("index.html"));

        mockMvc.perform(get("/"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Chưa build giao diện. Chạy npm run build trong thư mục frontend."))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/movies/m1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Chưa build giao diện. Chạy npm run build trong thư mục frontend."));
    }
}
