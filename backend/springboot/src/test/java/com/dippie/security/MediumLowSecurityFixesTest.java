package com.dippie.security;

import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogSeason;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.catalog.CatalogSnapshot;
import com.dippie.model.library.FavoriteItem;
import com.dippie.model.library.MovieRating;
import com.dippie.model.library.WatchlistItem;
import com.dippie.model.movie.Movie;
import com.dippie.model.progress.WatchProgress;
import com.dippie.model.user.Role;
import com.dippie.model.user.User;
import com.dippie.repository.admin.AdminAuditEventRepository;
import com.dippie.repository.catalog.CatalogEpisodeRepository;
import com.dippie.repository.catalog.CatalogSeasonRepository;
import com.dippie.repository.catalog.CatalogSeriesRepository;
import com.dippie.repository.catalog.CatalogSnapshotRepository;
import com.dippie.repository.library.FavoriteItemRepository;
import com.dippie.repository.library.MovieRatingRepository;
import com.dippie.repository.library.WatchlistItemRepository;
import com.dippie.repository.movie.MovieRepository;
import com.dippie.repository.progress.WatchProgressRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.service.catalog.CatalogReadModelCache;

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// test hồi quy phân quyền fail-closed, lọc phim nháp khỏi thư viện với thống kê, ghi audit và đóng gói H2
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MediumLowSecurityFixesTest {

    private static final String READY = "{\"status\":\"ready\"}";
    private static final String DRAFT = "{\"status\":\"draft\"}";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final MovieRepository movieRepository;
    private final CatalogSeriesRepository seriesRepository;
    private final CatalogSeasonRepository seasonRepository;
    private final CatalogEpisodeRepository episodeRepository;
    private final CatalogSnapshotRepository snapshotRepository;
    private final WatchlistItemRepository watchlistRepository;
    private final FavoriteItemRepository favoriteRepository;
    private final MovieRatingRepository ratingRepository;
    private final WatchProgressRepository watchProgressRepository;
    private final AdminAuditEventRepository auditEventRepository;
    private final CatalogReadModelCache readModelCache;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;

    MediumLowSecurityFixesTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            MovieRepository movieRepository,
            CatalogSeriesRepository seriesRepository,
            CatalogSeasonRepository seasonRepository,
            CatalogEpisodeRepository episodeRepository,
            CatalogSnapshotRepository snapshotRepository,
            WatchlistItemRepository watchlistRepository,
            FavoriteItemRepository favoriteRepository,
            MovieRatingRepository ratingRepository,
            WatchProgressRepository watchProgressRepository,
            AdminAuditEventRepository auditEventRepository,
            CatalogReadModelCache readModelCache,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginRateLimiter loginRateLimiter
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.movieRepository = movieRepository;
        this.seriesRepository = seriesRepository;
        this.seasonRepository = seasonRepository;
        this.episodeRepository = episodeRepository;
        this.snapshotRepository = snapshotRepository;
        this.watchlistRepository = watchlistRepository;
        this.favoriteRepository = favoriteRepository;
        this.ratingRepository = ratingRepository;
        this.watchProgressRepository = watchProgressRepository;
        this.auditEventRepository = auditEventRepository;
        this.readModelCache = readModelCache;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @BeforeEach
    void setUp() {
        loginRateLimiter.reset();
        auditEventRepository.deleteAll();
        watchProgressRepository.deleteAll();
        watchlistRepository.deleteAll();
        favoriteRepository.deleteAll();
        ratingRepository.deleteAll();
        snapshotRepository.deleteAll();
        episodeRepository.deleteAll();
        seasonRepository.deleteAll();
        seriesRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.deleteAll();
        readModelCache.invalidate();
    }

    // check login email không phân biệt hoa thường, route API lạ đòi đăng nhập và giấu thông tin LLM với user thường
    @Test
    void enforcesFailClosedRoutesEmailNormalizationAndAssistantStatusPrivacy() throws Exception {
        User viewer = userRepository.saveAndFlush(new User(
                "alice_sec", "alice.sec@example.com", passwordEncoder.encode("secret123"), "Alice",
                Role.ROLE_USER, LocalDateTime.of(2026, 3, 1, 10, 0)
        ));
        User admin = userRepository.saveAndFlush(new User(
                "admin_sec", "admin.sec@dippie.com", passwordEncoder.encode("StrongAdminPass123!"), "Admin",
                Role.ROLE_ADMIN, LocalDateTime.of(2026, 3, 1, 10, 0)
        ));
        String userBearer = "Bearer " + jwtService.issueLoginToken(viewer.getUsername(), viewer.getTokenVersion());
        String adminBearer = "Bearer " + jwtService.issueLoginToken(admin.getUsername(), admin.getTokenVersion());

        // gõ email viết hoa lúc đăng nhập vẫn phải hạ về chữ thường
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "usernameOrEmail", "ALICE.SEC@EXAMPLE.COM",
                                "password", "secret123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice_sec"));

        // route API không permitAll thì chưa login ăn 401, đụng /api/admin/** ăn 403
        mockMvc.perform(get("/api/unmapped-endpoint"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập để tiếp tục."));
        mockMvc.perform(post("/api/movies"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isForbidden());

        // /api/assistant/status giấu llmProvider với documents khỏi user thường, mỗi admin xem đủ
        mockMvc.perform(get("/api/assistant/status").header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.searchReady").value(true))
                .andExpect(jsonPath("$.data.llmProvider").isEmpty())
                .andExpect(jsonPath("$.data.documents").isEmpty());
        mockMvc.perform(get("/api/assistant/status").header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.llmProvider").value("template"));
    }

    // phim còn nháp phải biến khỏi watchlist, yêu thích, chấm điểm lẫn thống kê admin
    @Test
    void hidesUnpublishedSeriesFromUserLibraryAndAdminStats() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 3, 1, 10, 0);
        User viewer = userRepository.saveAndFlush(new User(
                "lib_viewer", "lib.viewer@example.com", passwordEncoder.encode("secret123"), "Viewer",
                Role.ROLE_USER, now
        ));
        User admin = userRepository.saveAndFlush(new User(
                "lib_admin", "lib.admin@dippie.com", passwordEncoder.encode("StrongAdminPass123!"), "Admin",
                Role.ROLE_ADMIN, now
        ));
        String userBearer = "Bearer " + jwtService.issueLoginToken(viewer.getUsername(), viewer.getTokenVersion());
        String adminBearer = "Bearer " + jwtService.issueLoginToken(admin.getUsername(), admin.getTokenVersion());

        movieRepository.saveAndFlush(new Movie("ser_pub", "Phim Xuất Bản", 2025, 8.5, "20m", "Animation", null, null, null, null));
        movieRepository.saveAndFlush(new Movie("ser_draft", "Phim Nháp", 2025, 9.9, "20m", "Animation", null, null, null, null));
        seriesRepository.saveAndFlush(new CatalogSeries(
                "ser_pub", "Phim Xuất Bản", null, null, null, null, null, null, null, null, null,
                2025, null, null, null, null, null, null, null, null, null, READY, null, 1
        ));
        seriesRepository.saveAndFlush(new CatalogSeries(
                "ser_draft", "Phim Nháp", null, null, null, null, null, null, null, null, null,
                2025, null, null, null, null, null, null, null, null, null, DRAFT, null, 1
        ));
        seasonRepository.saveAndFlush(new CatalogSeason(
                "sea_draft", "ser_pub", 1, "Mùa nháp", null, "regular", null, null, null, null, DRAFT, null, 1
        ));
        episodeRepository.saveAndFlush(new CatalogEpisode(
                "ep_pub_ok", "ser_pub", null, 1, "Tập công khai", null, null, null, "regular", null,
                120_000L, null, null, null, null, null, null, null, null, null, null, null, null, READY, null, 1
        ));
        episodeRepository.saveAndFlush(new CatalogEpisode(
                "ep_in_draft_season", "ser_pub", "sea_draft", 2, "Tập trong mùa nháp", null, null, null, "regular", null,
                120_000L, null, null, null, null, null, null, null, null, null, null, null, null, READY, null, 1
        ));
        episodeRepository.saveAndFlush(new CatalogEpisode(
                "ep_in_draft_series", "ser_draft", null, 1, "Tập trong bộ nháp", null, null, null, "regular", null,
                120_000L, null, null, null, null, null, null, null, null, null, null, null, null, READY, null, 1
        ));

        watchlistRepository.saveAndFlush(new WatchlistItem(viewer.getId(), "ser_pub", now));
        watchlistRepository.saveAndFlush(new WatchlistItem(viewer.getId(), "ser_draft", now));
        favoriteRepository.saveAndFlush(new FavoriteItem(viewer.getId(), "ser_pub", now));
        favoriteRepository.saveAndFlush(new FavoriteItem(viewer.getId(), "ser_draft", now));
        ratingRepository.saveAndFlush(new MovieRating(viewer.getId(), "ser_pub", 8, now));
        ratingRepository.saveAndFlush(new MovieRating(viewer.getId(), "ser_draft", 10, now));
        watchProgressRepository.saveAndFlush(WatchProgress.startAt(viewer.getId(), "ep_pub_ok", "ast_1", 10_000L, now));
        watchProgressRepository.saveAndFlush(WatchProgress.startAt(viewer.getId(), "ep_in_draft_season", "ast_2", 10_000L, now));
        watchProgressRepository.saveAndFlush(WatchProgress.startAt(viewer.getId(), "ep_in_draft_series", "ast_3", 10_000L, now));
        readModelCache.invalidate();

        // thư viện chỉ trả ser_pub thôi, bỏ ser_draft ra
        mockMvc.perform(get("/api/users/watchlist").header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].movieId").value("ser_pub"));
        mockMvc.perform(get("/api/users/favorites").header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].movieId").value("ser_pub"));
        mockMvc.perform(get("/api/users/ratings").header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].movieId").value("ser_pub"));

        // thống kê admin chỉ đếm tập nào mà cả bộ, mùa lẫn tập đều đã xuất bản
        mockMvc.perform(get("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publishedEpisodes").value(1))
                .andExpect(jsonPath("$.data.topWatchedEpisodes.length()").value(1))
                .andExpect(jsonPath("$.data.topWatchedEpisodes[0].episodeId").value("ep_pub_ok"))
                .andExpect(jsonPath("$.data.topRatedMovies.length()").value(1))
                .andExpect(jsonPath("$.data.topRatedMovies[0].movieId").value("ser_pub"));
    }

    // vai trò hay trạng thái không đổi thì khỏi ghi audit, còn retry snapshot với export phải ghi, GET snapshots chỉ đọc thôi
    @Test
    void skipsNoOpUserAuditsAndAuditsCatalogRetryAndExportWithoutMutatingOnGetSnapshots() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 3, 1, 10, 0);
        User admin = userRepository.saveAndFlush(new User(
                "audit_admin", "audit.admin@dippie.com", passwordEncoder.encode("StrongAdminPass123!"), "Admin",
                Role.ROLE_ADMIN, now
        ));
        User viewer = userRepository.saveAndFlush(new User(
                "audit_viewer", "audit.viewer@example.com", passwordEncoder.encode("secret123"), "Viewer",
                Role.ROLE_USER, now
        ));
        String adminBearer = "Bearer " + jwtService.issueLoginToken(admin.getUsername(), admin.getTokenVersion());
        long originalVersion = viewer.getTokenVersion();

        // gửi y chang quyền với trạng thái cũ thì đừng tăng tokenVersion hay đẻ thêm audit
        mockMvc.perform(patch("/api/admin/users/" + viewer.getId() + "/role")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROLE_USER\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/admin/users/" + viewer.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(viewer.getId()).orElseThrow().getTokenVersion()).isEqualTo(originalVersion);
        assertThat(auditEventRepository.count()).isZero();

        // GET /api/admin/catalog/snapshots chỉ đọc thôi, đừng tự đổi trạng thái snapshot đang BUILDING
        snapshotRepository.saveAndFlush(new CatalogSnapshot("snap_ro_1", 1L, "hash1", 1, "BUILDING", 0, null, null));
        mockMvc.perform(get("/api/admin/catalog/snapshots").header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("BUILDING"));
        assertThat(snapshotRepository.findById("snap_ro_1").orElseThrow().getStatus()).isEqualTo("BUILDING");

        // retry snapshot với export catalog đều phải ghi audit nha
        mockMvc.perform(post("/api/admin/catalog/snapshots/snap_ro_1/retry")
                        .header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/catalog/export").header(HttpHeaders.AUTHORIZATION, adminBearer))
                .andExpect(status().isOk());

        var actions = auditEventRepository.findAll().stream().map(event -> event.getAction()).toList();
        assertThat(actions).contains("CATALOG_SNAPSHOT_RETRIED", "CATALOG_EXPORTED");
    }

    // check triggers.sql bỏ FOR SHARE kẻo kẹt lock, H2 giữ scope test trong pom.xml còn script chạy thì bật demo
    @Test
    void verifiesTriggerLockFixAndH2TestScopeInPomAndStartScript() throws Exception {
        String triggers = Files.readString(Path.of("../../database/objects/triggers.sql"));
        assertThat(triggers).doesNotContainIgnoringCase("FOR SHARE");

        String pom = Files.readString(Path.of("pom.xml"));
        assertThat(pom).contains("<artifactId>h2</artifactId>");
        assertThat(pom).contains("<id>demo</id>");
        int firstH2 = pom.indexOf("<artifactId>h2</artifactId>");
        int firstScopeEnd = pom.indexOf("</scope>", firstH2);
        assertThat(pom.substring(firstH2, firstScopeEnd)).contains("<scope>test");

        String startScript = Files.readString(Path.of("../../start-java.ps1"));
        assertThat(startScript).contains("-Pdemo");
    }
}
