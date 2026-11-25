package com.dippie.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippie.admin.AdminAuditEvent;
import com.dippie.admin.AdminAuditEventRepository;
import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogEpisodeRepository;
import com.dippie.catalog.domain.CatalogSeason;
import com.dippie.catalog.domain.CatalogSeasonRepository;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.domain.CatalogSeriesRepository;
import com.dippie.security.JwtService;
import com.dippie.security.Role;
import com.dippie.user.User;
import com.dippie.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
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

// Test tích hợp cho luồng báo lỗi phát video: khách và thành viên gửi báo lỗi, kiểm tra dữ liệu, giới hạn IP và admin xử lý kèm nhật ký.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PlaybackReportControllerTest {

    private static final String READY = "{\"status\":\"ready\"}";
    private static final String DRAFT = "{\"status\":\"draft\"}";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final PlaybackReportRepository reportRepository;
    private final PlaybackReportRateLimiter rateLimiter;
    private final CatalogSeriesRepository seriesRepository;
    private final CatalogSeasonRepository seasonRepository;
    private final CatalogEpisodeRepository episodeRepository;
    private final UserRepository userRepository;
    private final AdminAuditEventRepository auditEventRepository;
    private final JwtService jwtService;

    private User regularUser;
    private User adminUser;
    private String userBearer;
    private String adminBearer;

    PlaybackReportControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            PlaybackReportRepository reportRepository,
            PlaybackReportRateLimiter rateLimiter,
            CatalogSeriesRepository seriesRepository,
            CatalogSeasonRepository seasonRepository,
            CatalogEpisodeRepository episodeRepository,
            UserRepository userRepository,
            AdminAuditEventRepository auditEventRepository,
            JwtService jwtService
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.reportRepository = reportRepository;
        this.rateLimiter = rateLimiter;
        this.seriesRepository = seriesRepository;
        this.seasonRepository = seasonRepository;
        this.episodeRepository = episodeRepository;
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;
        this.jwtService = jwtService;
    }

    @BeforeEach
    void setUp() {
        rateLimiter.reset();
        reportRepository.deleteAll();
        auditEventRepository.deleteAll();
        episodeRepository.deleteAll();
        seasonRepository.deleteAll();
        seriesRepository.deleteAll();
        userRepository.deleteAll();

        regularUser = userRepository.saveAndFlush(new User(
                "reporter", "reporter@example.com", "pw", "Người xem", Role.ROLE_USER,
                LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        adminUser = userRepository.saveAndFlush(new User(
                "mod_admin", "mod@dippie.com", "pw", "Quản trị", Role.ROLE_ADMIN,
                LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        userBearer = "Bearer " + jwtService.issueLoginToken(regularUser.getUsername(), regularUser.getTokenVersion());
        adminBearer = "Bearer " + jwtService.issueLoginToken(adminUser.getUsername(), adminUser.getTokenVersion());

        seriesRepository.saveAndFlush(new CatalogSeries(
                "ser_doraemon", "Doraemon", null, null, null, null, null, null, null, null, null,
                2020, null, null, null, null, null, null, null, null, null, READY, null, 1
        ));
        seasonRepository.saveAndFlush(new CatalogSeason(
                "sea_doraemon_s05", "ser_doraemon", 5, "Mùa 5", null, "regular", null,
                null, null, null, READY, null, 1
        ));
        episodeRepository.saveAndFlush(new CatalogEpisode(
                "ep_doraemon_1", "ser_doraemon", "sea_doraemon_s05", 1, "Tập 1: Cỗ máy thời gian", null, null, null,
                "regular", null, 600_000L, null, null, null, null, null, null, null, null, null,
                null, null, null, READY, null, 1
        ));
        episodeRepository.saveAndFlush(new CatalogEpisode(
                "ep_doraemon_s05_e002", "ser_doraemon", "sea_doraemon_s05", 2, "Tập 2: Cánh cửa thần kỳ", null, null, null,
                "regular", null, 600_000L, null, null, null, null, null, null, null, null, null,
                null, null, null, READY, null, 1
        ));
        episodeRepository.saveAndFlush(new CatalogEpisode(
                "ep_doraemon_draft", "ser_doraemon", "sea_doraemon_s05", 3, "Tập nháp", null, null, null,
                "regular", null, 600_000L, null, null, null, null, null, null, null, null, null,
                null, null, null, DRAFT, null, 1
        ));
    }

    // Cả khách ẩn danh lẫn tài khoản đã đăng nhập đều gửi được báo lỗi, và danh sách admin nhận đủ tên bộ, tên tập cùng mốc UTC.
    @Test
    void acceptsAnonymousAndAuthenticatedReportsAndEnrichesAdminListing() throws Exception {
        MvcResult anonResult = mockMvc.perform(post("/api/reports/playback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "episodeId", "ep_doraemon_1",
                                "assetId", "youtube:abc123",
                                "reason", "khong_phat",
                                "note", "Màn hình đen sau 5 giây"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();
        long anonReportId = objectMapper.readTree(anonResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        MvcResult authResult = mockMvc.perform(post("/api/reports/playback")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"episodeId": "ep_doraemon_s05_e002", "assetId": null, "reason": "am_thanh", "note": "Mất tiếng"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();
        long authReportId = objectMapper.readTree(authResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        assertThat(reportRepository.findById(anonReportId).orElseThrow().getReporterUserId()).isNull();
        assertThat(reportRepository.findById(authReportId).orElseThrow().getReporterUserId())
                .isEqualTo(regularUser.getId());

        // Admin lọc trạng thái open phải thấy đúng hai báo lỗi vừa gửi với thời gian ISO-8601 UTC.
        MvcResult listResult = mockMvc.perform(get("/api/admin/reports?status=open")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(authReportId))
                .andExpect(jsonPath("$.data[0].episodeId").value("ep_doraemon_s05_e002"))
                .andExpect(jsonPath("$.data[0].seriesTitle").value("Doraemon"))
                .andExpect(jsonPath("$.data[0].episodeTitle").value("Tập 2: Cánh cửa thần kỳ"))
                .andExpect(jsonPath("$.data[0].reason").value("am_thanh"))
                .andExpect(jsonPath("$.data[0].status").value("open"))
                .andExpect(jsonPath("$.data[1].id").value(anonReportId))
                .andExpect(jsonPath("$.data[1].episodeId").value("ep_doraemon_1"))
                .andExpect(jsonPath("$.data[1].seriesTitle").value("Doraemon"))
                .andExpect(jsonPath("$.data[1].episodeTitle").value("Tập 1: Cỗ máy thời gian"))
                .andExpect(jsonPath("$.data[1].assetId").value("youtube:abc123"))
                .andReturn();

        JsonNode items = objectMapper.readTree(listResult.getResponse().getContentAsString()).path("data");
        String createdAtText = items.get(1).path("createdAt").asText();
        assertThat(createdAtText).endsWith("Z");
        assertThat(Instant.parse(createdAtText)).isNotNull();
    }

    // Từ chối lý do lạ, ghi chú quá 500 ký tự, episodeId sai kiểu hoặc tập chưa đăng bằng mã 400.
    @Test
    void rejectsInvalidReasonOverlongNoteNumericAndUnpublishedEpisodeIdWith400() throws Exception {
        mockMvc.perform(post("/api/reports/playback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"episodeId": "ep_doraemon_1", "reason": "khong_ton_tai"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vui lòng chọn một lý do báo lỗi hợp lệ."));

        mockMvc.perform(post("/api/reports/playback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "episodeId", "ep_doraemon_1",
                                "reason", "khac",
                                "note", "a".repeat(501)
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ghi chú không được dài quá 500 ký tự."));

        mockMvc.perform(post("/api/reports/playback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"episodeId": true, "reason": "sai_tap"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã tập phim không hợp lệ."));

        mockMvc.perform(post("/api/reports/playback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"episodeId": 1001, "reason": "sai_tap"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mã tập phim không hợp lệ."));

        mockMvc.perform(post("/api/reports/playback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"episodeId": "ep_khong_ton_tai", "reason": "khong_phat"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PlaybackReportService.EPISODE_NOT_PUBLISHED_MESSAGE));

        mockMvc.perform(post("/api/reports/playback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"episodeId": "ep_doraemon_draft", "reason": "khong_phat"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PlaybackReportService.EPISODE_NOT_PUBLISHED_MESSAGE));
    }

    // Vượt 10 lần từ cùng một IP phải trả 429 kèm header Retry-After, trong khi IP khác vẫn gửi được.
    @Test
    void enforcesPerClientRateLimitOnPublicReportEndpoint() throws Exception {
        String body = """
                {"episodeId": "ep_doraemon_1", "reason": "noi_dung_khong_phu_hop", "note": "Cần kiểm tra"}
                """;
        for (int attempt = 0; attempt < 10; attempt++) {
            mockMvc.perform(post("/api/reports/playback")
                            .with(request -> {
                                request.setRemoteAddr("10.10.0.1");
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/reports/playback")
                        .with(request -> {
                            request.setRemoteAddr("10.10.0.1");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Bạn đã gửi quá nhiều báo cáo trong thời gian ngắn. Vui lòng đợi một lát rồi thử lại."));

        // IP khác không bị ảnh hưởng bởi bộ đếm của IP đã vượt ngưỡng.
        mockMvc.perform(post("/api/reports/playback")
                        .with(request -> {
                            request.setRemoteAddr("10.10.0.2");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    // Chỉ admin mới xem và đổi trạng thái báo lỗi, mỗi lần xử lý đều phải ghi vào nhật ký quản trị.
    @Test
    void enforcesAdminAuthorizationUpdatesStatusAndWritesAuditEvent() throws Exception {
        PlaybackReport openReport = reportRepository.saveAndFlush(new PlaybackReport(
                "ep_doraemon_1", "youtube:abc123", regularUser.getId(),
                PlaybackReportReason.SAI_TAP, "Nhầm tập", LocalDateTime.of(2026, 3, 1, 9, 0)
        ));

        // Khách và tài khoản thường phải bị chặn 403 trên cả GET lẫn PATCH quản trị.
        mockMvc.perform(get("/api/admin/reports?status=open"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reports?status=open").header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/reports/" + openReport.getId())
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"resolved\"}"))
                .andExpect(status().isForbidden());

        // Bộ lọc hoặc trạng thái chuyển sai trả 400; báo cáo không tồn tại trả 404.
        mockMvc.perform(get("/api/admin/reports?status=unknown")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/reports/" + openReport.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"open\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/reports/999999")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"resolved\"}"))
                .andExpect(status().isNotFound());

        // Đổi trạng thái sang resolved rồi sang dismissed, xác nhận cả hai lần đều ghi nhật ký.
        mockMvc.perform(patch("/api/admin/reports/" + openReport.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"resolved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("resolved"))
                .andExpect(jsonPath("$.data.seriesTitle").value("Doraemon"));

        mockMvc.perform(get("/api/admin/reports?status=open").header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/admin/reports?status=resolved").header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(patch("/api/admin/reports/" + openReport.getId())
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"dismissed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("dismissed"));

        List<AdminAuditEvent> audits = auditEventRepository.findAll();
        assertThat(audits).hasSize(2);
        assertThat(audits.getFirst().getAction()).isEqualTo(PlaybackReportService.ACTION_PLAYBACK_REPORT_UPDATED);
        assertThat(audits.getFirst().getTargetType()).isEqualTo("PLAYBACK_REPORT");
        assertThat(audits.getFirst().getTargetId()).isEqualTo(String.valueOf(openReport.getId()));
    }
}
