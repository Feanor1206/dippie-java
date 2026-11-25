package com.dippie.progress;

import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogEpisodeRepository;
import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.catalog.domain.CatalogMediaAssetRepository;
import com.dippie.catalog.domain.CatalogSeason;
import com.dippie.catalog.domain.CatalogSeasonRepository;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.domain.CatalogSeriesRepository;
import com.dippie.media.MediaAsset;
import com.dippie.media.MediaAssetRepository;
import com.dippie.movie.Movie;
import com.dippie.movie.MovieRepository;
import com.dippie.security.JwtService;
import com.dippie.security.Role;
import com.dippie.user.User;
import com.dippie.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WatchProgressControllerTest {

    private static final String READY = "{\"status\":\"ready\"}";
    private static final String DRAFT = "{\"status\":\"draft\"}";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final MovieRepository movieRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final CatalogSeriesRepository seriesRepository;
    private final CatalogSeasonRepository seasonRepository;
    private final CatalogEpisodeRepository episodeRepository;
    private final CatalogMediaAssetRepository catalogMediaAssetRepository;
    private final WatchProgressRepository watchProgressRepository;
    private final JwtService jwtService;

    private User viewer;
    private User otherUser;
    private User adminUser;
    private String userAuth;
    private String adminAuth;

    WatchProgressControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            MovieRepository movieRepository,
            MediaAssetRepository mediaAssetRepository,
            CatalogSeriesRepository seriesRepository,
            CatalogSeasonRepository seasonRepository,
            CatalogEpisodeRepository episodeRepository,
            CatalogMediaAssetRepository catalogMediaAssetRepository,
            WatchProgressRepository watchProgressRepository,
            JwtService jwtService
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.movieRepository = movieRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.seriesRepository = seriesRepository;
        this.seasonRepository = seasonRepository;
        this.episodeRepository = episodeRepository;
        this.catalogMediaAssetRepository = catalogMediaAssetRepository;
        this.watchProgressRepository = watchProgressRepository;
        this.jwtService = jwtService;
    }

    @BeforeEach
    void setUp() {
        watchProgressRepository.deleteAll();
        mediaAssetRepository.deleteAll();
        episodeRepository.deleteAll();
        seasonRepository.deleteAll();
        seriesRepository.deleteAll();
        catalogMediaAssetRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.deleteAll();

        viewer = userRepository.saveAndFlush(new User(
                "progress_user", "progress@example.com", "pw", "Progress User",
                Role.ROLE_USER, LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        otherUser = userRepository.saveAndFlush(new User(
                "other_user", "other@example.com", "pw", "Other User",
                Role.ROLE_USER, LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        adminUser = userRepository.saveAndFlush(new User(
                "progress_admin", "admin@example.com", "pw", "Admin User",
                Role.ROLE_ADMIN, LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        userAuth = "Bearer " + jwtService.issueLoginToken(viewer.getUsername());
        adminAuth = "Bearer " + jwtService.issueLoginToken(adminUser.getUsername());

        movieRepository.saveAndFlush(new Movie(
                "the-last-orbit", "The Last Orbit", 2024, 8.8, "100 min", "Sci-Fi", null, null, null, null
        ));
    }

    @Test
    void requiresAuthenticationOnAllEndpoints() throws Exception {
        mockMvc.perform(get("/api/users/watch-progress"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Chưa xác thực người dùng"));

        mockMvc.perform(get("/api/users/watch-progress/ep_shin_1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Chưa xác thực người dùng"));

        mockMvc.perform(put("/api/users/watch-progress/ep_shin_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"ast_shin_1\",\"expectedVersion\":-1,\"positionMs\":0}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Chưa xác thực người dùng"));

        mockMvc.perform(delete("/api/users/watch-progress/ep_shin_1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Chưa xác thực người dùng"));
    }

    @Test
    void createsAdvancesDetectsStaleVersionValidatesPositionAndDeletes() throws Exception {
        seedShinEpisode(600_000L, 600_000L);

        mockMvc.perform(put("/api/users/watch-progress/ep_shin_1")
                        .header(HttpHeaders.AUTHORIZATION, userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"ast_shin_1\",\"expectedVersion\":-1,\"positionMs\":120000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã lưu tiến độ xem"))
                .andExpect(jsonPath("$.data.episodeId").value("ep_shin_1"))
                .andExpect(jsonPath("$.data.seriesId").value("ser_shin"))
                .andExpect(jsonPath("$.data.seriesTitle").value("Shin Cậu Bé Bút Chì"))
                .andExpect(jsonPath("$.data.seasonId").value("sea_shin_1"))
                .andExpect(jsonPath("$.data.seasonNumber").value(1))
                .andExpect(jsonPath("$.data.seasonTitle").value("Mùa 1"))
                .andExpect(jsonPath("$.data.episodeNumber").value(1))
                .andExpect(jsonPath("$.data.episodeTitle").value("Tập 1: Đi siêu thị"))
                .andExpect(jsonPath("$.data.assetId").value("ast_shin_1"))
                .andExpect(jsonPath("$.data.positionMs").value(120000))
                .andExpect(jsonPath("$.data.durationMs").value(600000))
                .andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.version").value(0));

        mockMvc.perform(put("/api/users/watch-progress/ep_shin_1")
                        .header(HttpHeaders.AUTHORIZATION, userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"ast_shin_1\",\"expectedVersion\":-1,\"positionMs\":150000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Watch progress changed since it was loaded; refresh and retry."));

        mockMvc.perform(put("/api/users/watch-progress/ep_shin_1")
                        .header(HttpHeaders.AUTHORIZATION, userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"ast_shin_1\",\"expectedVersion\":0,\"positionMs\":590000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(put("/api/users/watch-progress/ep_shin_1")
                        .header(HttpHeaders.AUTHORIZATION, userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"ast_shin_1\",\"expectedVersion\":0,\"positionMs\":595000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Watch progress changed since it was loaded; refresh and retry."));

        mockMvc.perform(put("/api/users/watch-progress/ep_shin_1")
                        .header(HttpHeaders.AUTHORIZATION, userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"ast_shin_1\",\"expectedVersion\":1,\"positionMs\":600001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vị trí xem vượt quá thời lượng video."));

        for (String invalidBody : List.of(
                "{\"assetId\":\"ast_shin_1\",\"expectedVersion\":1,\"positionMs\":true}",
                "{\"assetId\":\"ast_shin_1\",\"expectedVersion\":1,\"positionMs\":12.5}",
                "{\"assetId\":\"ast_shin_1\",\"expectedVersion\":1,\"positionMs\":\"100\"}",
                "{\"assetId\":\"ast_shin_1\",\"expectedVersion\":1,\"positionMs\":-5}"
        )) {
            mockMvc.perform(put("/api/users/watch-progress/ep_shin_1")
                            .header(HttpHeaders.AUTHORIZATION, userAuth)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("Vị trí xem phải là số nguyên từ 0 đến 24 giờ, tính bằng mili giây."));
        }

        mockMvc.perform(delete("/api/users/watch-progress/ep_shin_1")
                        .header(HttpHeaders.AUTHORIZATION, userAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã xóa tiến độ xem"));

        mockMvc.perform(get("/api/users/watch-progress/ep_shin_1")
                        .header(HttpHeaders.AUTHORIZATION, userAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy tập có thể phát: ep_shin_1"));
    }

    @Test
    void concurrentDuplicateCreateAndUpdateReturnOne200AndOne409() throws Exception {
        seedShinEpisode(600_000L, 600_000L);
        String createBody = "{\"assetId\":\"ast_shin_1\",\"expectedVersion\":-1,\"positionMs\":10000}";
        CyclicBarrier createBarrier = new CyclicBarrier(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> createTask = () -> {
                createBarrier.await();
                return mockMvc.perform(put("/api/users/watch-progress/ep_shin_1")
                                .header(HttpHeaders.AUTHORIZATION, userAuth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            List<Future<Integer>> results = executor.invokeAll(List.of(createTask, createTask));
            List<Integer> statuses = List.of(results.get(0).get(), results.get(1).get())
                    .stream()
                    .sorted()
                    .toList();
            assertThat(statuses).containsExactly(200, 409);

            String updateBody = "{\"assetId\":\"ast_shin_1\",\"expectedVersion\":0,\"positionMs\":20000}";
            CyclicBarrier updateBarrier = new CyclicBarrier(2);
            Callable<Integer> updateTask = () -> {
                updateBarrier.await();
                return mockMvc.perform(put("/api/users/watch-progress/ep_shin_1")
                                .header(HttpHeaders.AUTHORIZATION, userAuth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateBody))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            };
            List<Future<Integer>> updateResults = executor.invokeAll(List.of(updateTask, updateTask));
            List<Integer> updateStatuses = List.of(updateResults.get(0).get(), updateResults.get(1).get())
                    .stream()
                    .sorted()
                    .toList();
            assertThat(updateStatuses).containsExactly(200, 409);
        }
    }

    @Test
    void recentProgressCapsAtThirtyAndFiltersIneligibleEntries() throws Exception {
        seriesRepository.saveAndFlush(new CatalogSeries(
                "s", "Series", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, READY, null, 1
        ));
        seasonRepository.saveAllAndFlush(List.of(
                new CatalogSeason("season", "s", 1, "Season", null, "regular", null,
                        null, null, null, READY, null, 1),
                new CatalogSeason("draft-season", "s", 2, "Draft Season", null, "regular", null,
                        null, null, null, DRAFT, null, 1)
        ));

        LocalDateTime baseTime = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        for (int i = 0; i < 32; i++) {
            String epId = "ep" + i;
            String assetId = "asset" + i;
            String seasonId = switch (i) {
                case 30 -> null;
                case 25 -> "draft-season";
                default -> "season";
            };
            episodeRepository.saveAndFlush(new CatalogEpisode(
                    epId, "s", seasonId, i, "Episode " + i, null, null, null,
                    "regular", null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, READY, null, 1
            ));
            String scope = i == 28 ? "partial" : "full_episode";
            String coverage = "[{\"episode_id\":\"" + epId + "\",\"scope\":\"" + scope + "\"}]";
            catalogMediaAssetRepository.saveAndFlush(new CatalogMediaAsset(
                    assetId, "video", i == 31 ? "dub" : "feature", null, null, null,
                    "local", "local", "test.mp4", null, null, 1024L, null, 100_000L,
                    null, coverage, null, i == 27 ? "private" : "public", true, READY, null, 1
            ));
            if (i != 26) {
                mediaAssetRepository.saveAndFlush(new MediaAsset(
                        assetId, "the-last-orbit", "feature", "local", null,
                        "test.mp4", "test" + i, "video/mp4", 1024L, 100.0, true, i != 29
                ));
            }
            watchProgressRepository.saveAndFlush(WatchProgress.startAt(
                    viewer.getId(), epId, assetId, i * 1000L, baseTime.plusMinutes(i)
            ));
        }
        watchProgressRepository.saveAndFlush(WatchProgress.startAt(
                otherUser.getId(), "ep0", "asset0", 0L, LocalDateTime.of(2025, 1, 1, 0, 0, 0)
        ));

        MvcResult listResult = mockMvc.perform(get("/api/users/watch-progress")
                        .header(HttpHeaders.AUTHORIZATION, userAuth))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper.readTree(listResult.getResponse().getContentAsString()).path("data");

        Set<Integer> excluded = Set.of(25, 26, 27, 28, 29);
        List<String> expectedIds = new ArrayList<>();
        for (int i = 30; i >= 2; i--) {
            if (!excluded.contains(i)) {
                expectedIds.add("ep" + i);
            }
        }
        List<String> actualIds = new ArrayList<>();
        for (JsonNode row : rows) {
            actualIds.add(row.path("episodeId").asText());
        }
        assertThat(actualIds).containsExactlyElementsOf(expectedIds);
        assertThat(rows.get(0).path("seasonId").isNull()).isTrue();
        assertThat(rows.get(0).path("positionMs").longValue()).isEqualTo(30_000L);

        for (JsonNode row : rows) {
            MvcResult single = mockMvc.perform(get("/api/users/watch-progress/" + row.path("episodeId").asText())
                            .header(HttpHeaders.AUTHORIZATION, userAuth))
                    .andExpect(status().isOk())
                    .andReturn();
            assertThat(objectMapper.readTree(single.getResponse().getContentAsString()).path("data")).isEqualTo(row);
        }

        for (int i : List.of(25, 26, 27, 28, 29, 31)) {
            mockMvc.perform(get("/api/users/watch-progress/ep" + i)
                            .header(HttpHeaders.AUTHORIZATION, userAuth))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Không tìm thấy tập có thể phát: ep" + i));
        }

        mockMvc.perform(put("/api/users/watch-progress/ep27")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"asset27\",\"expectedVersion\":-1,\"positionMs\":5000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.episodeId").value("ep27"));
    }

    private void seedShinEpisode(Long episodeDurationMs, Long assetDurationMs) {
        seriesRepository.saveAndFlush(new CatalogSeries(
                "ser_shin", "Shin Cậu Bé Bút Chì", null, null, "Phim hoạt hình", null, null, null, null,
                null, null, 2024, null, null, null, null, null, null, null, null, null, READY, null, 1
        ));
        seasonRepository.saveAndFlush(new CatalogSeason(
                "sea_shin_1", "ser_shin", 1, "Mùa 1", null, "regular", null,
                null, null, null, READY, null, 1
        ));
        episodeRepository.saveAndFlush(new CatalogEpisode(
                "ep_shin_1", "ser_shin", "sea_shin_1", 1, "Tập 1: Đi siêu thị", null, null, null,
                "regular", null, episodeDurationMs, null, null, null, null, null, null, null,
                null, null, null, null, null, READY, null, 1
        ));
        catalogMediaAssetRepository.saveAndFlush(new CatalogMediaAsset(
                "ast_shin_1", "video", "feature", null, null, null,
                "local", "local_main", "synthetic/feature_test.mp4", null, null, 1_024_000L, null, assetDurationMs,
                null, "[{\"episode_id\":\"ep_shin_1\",\"scope\":\"full_episode\"}]", null,
                "public", true, READY, null, 1
        ));
        mediaAssetRepository.saveAndFlush(new MediaAsset(
                "ast_shin_1", "the-last-orbit", "feature", "local", "local_main",
                "synthetic/feature_test.mp4", "shin-v1", "video/mp4", 1_024_000L, 600.0, true, true
        ));
    }
}
