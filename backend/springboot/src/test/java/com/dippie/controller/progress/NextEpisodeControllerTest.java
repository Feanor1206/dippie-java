package com.dippie.controller.progress;

import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogMediaAsset;
import com.dippie.model.catalog.CatalogSeason;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.media.MediaAsset;
import com.dippie.model.movie.Movie;
import com.dippie.model.progress.WatchProgress;
import com.dippie.model.user.Role;
import com.dippie.model.user.User;
import com.dippie.repository.catalog.CatalogEpisodeRepository;
import com.dippie.repository.catalog.CatalogMediaAssetRepository;
import com.dippie.repository.catalog.CatalogSeasonRepository;
import com.dippie.repository.catalog.CatalogSeriesRepository;
import com.dippie.repository.media.MediaAssetRepository;
import com.dippie.repository.movie.MovieRepository;
import com.dippie.repository.progress.WatchProgressRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.JwtService;
import com.dippie.service.catalog.CatalogReadModelCache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Test tích hợp cho NextEpisodeController: bỏ qua tập không phát được, chặn rò rỉ tiến độ của asset đã đổi quyền và giữ đúng thứ tự khi trùng giây.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class NextEpisodeControllerTest {

    private static final String READY = "{\"status\":\"ready\"}";

    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final MovieRepository movieRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final CatalogSeriesRepository seriesRepository;
    private final CatalogSeasonRepository seasonRepository;
    private final CatalogEpisodeRepository episodeRepository;
    private final CatalogMediaAssetRepository catalogMediaAssetRepository;
    private final WatchProgressRepository watchProgressRepository;
    private final CatalogReadModelCache catalogReadModelCache;
    private final JwtService jwtService;

    private User member;
    private User admin;
    private String memberAuth;
    private String adminAuth;

    NextEpisodeControllerTest(
            MockMvc mockMvc,
            UserRepository userRepository,
            MovieRepository movieRepository,
            MediaAssetRepository mediaAssetRepository,
            CatalogSeriesRepository seriesRepository,
            CatalogSeasonRepository seasonRepository,
            CatalogEpisodeRepository episodeRepository,
            CatalogMediaAssetRepository catalogMediaAssetRepository,
            WatchProgressRepository watchProgressRepository,
            CatalogReadModelCache catalogReadModelCache,
            JwtService jwtService
    ) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.movieRepository = movieRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.seriesRepository = seriesRepository;
        this.seasonRepository = seasonRepository;
        this.episodeRepository = episodeRepository;
        this.catalogMediaAssetRepository = catalogMediaAssetRepository;
        this.watchProgressRepository = watchProgressRepository;
        this.catalogReadModelCache = catalogReadModelCache;
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
        catalogReadModelCache.invalidate();

        member = userRepository.saveAndFlush(new User(
                "next_ep_user", "next_ep_user@example.com", "pw", "Next Ep User",
                Role.ROLE_USER, LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        admin = userRepository.saveAndFlush(new User(
                "next_ep_admin", "next_ep_admin@example.com", "pw", "Next Ep Admin",
                Role.ROLE_ADMIN, LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        memberAuth = "Bearer " + jwtService.issueLoginToken(member.getUsername());
        adminAuth = "Bearer " + jwtService.issueLoginToken(admin.getUsername());
        movieRepository.saveAndFlush(new Movie(
                "next-ep-anchor", "Next Ep Anchor", 2024, 8.0, "10m", "animation", null, null, null, null
        ));
    }

    @Test
    void skipsUnplayableYoutubeWatchOnlyAndDisabledEpisodesAcrossSeasons() throws Exception {
        seriesRepository.saveAndFlush(new CatalogSeries(
                "ser_ori_test", "Công chúa Ori", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, READY, null, 1
        ));
        seasonRepository.saveAllAndFlush(List.of(
                new CatalogSeason("sea_ori_1", "ser_ori_test", 1, "Mùa 1", null, "regular", null,
                        null, null, null, READY, null, 1),
                new CatalogSeason("sea_ori_2", "ser_ori_test", 2, "Mùa 2", null, "regular", null,
                        null, null, null, READY, null, 1)
        ));
        episodeRepository.saveAllAndFlush(List.of(
                episode("ep_ori_1", "ser_ori_test", "sea_ori_1", 1, 600_000L,
                        "{\"external_ids\":[{\"namespace\":\"youtube_playback\",\"value\":\"OriPlay0001\"}]}"),
                // Tập chỉ có youtube_watch (bị tắt nhúng) và không có file nội bộ nào, giống 44 tập thật của Ori
                episode("ep_ori_2_watch_only", "ser_ori_test", "sea_ori_1", 2, 600_000L,
                        "{\"external_ids\":[{\"namespace\":\"youtube_watch\",\"value\":\"OriWatch002\"}]}"),
                // Tập tắt nhúng kèm duy nhất một file đã bị tắt enabled=false
                episode("ep_ori_3_disabled_file", "ser_ori_test", "sea_ori_1", 3, 600_000L,
                        "{\"external_ids\":[{\"namespace\":\"youtube_watch\",\"value\":\"OriWatch003\"}]}"),
                // Tập chỉ có file private -> chỉ admin mới phát được
                episode("ep_ori_4_private_file", "ser_ori_test", "sea_ori_1", 4, 600_000L, null),
                episode("ep_ori_s2_1", "ser_ori_test", "sea_ori_2", 1, 600_000L,
                        "{\"external_ids\":[{\"namespace\":\"youtube_playback\",\"value\":\"OriPlayS201\"}]}")
        ));
        seedFileAsset("ast_ori_3", "ep_ori_3_disabled_file", "public", false, true);
        seedFileAsset("ast_ori_4", "ep_ori_4_private_file", "private", true, true);
        catalogReadModelCache.invalidate();

        // Thành viên thường: bắt đầu ở tập 1
        mockMvc.perform(get("/api/users/next-episode").param("seriesId", "ser_ori_test")
                        .header(HttpHeaders.AUTHORIZATION, memberAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.episodeId").value("ep_ori_1"))
                .andExpect(jsonPath("$.data.resumePositionMs").value(0))
                .andExpect(jsonPath("$.data.reason").value("NEXT_UNWATCHED"));

        // Xem xong tập 1 (đúng ngưỡng 95% = 570s / 600s) -> phải nhảy qua tập 2, 3, 4 sang Mùa 2 Tập 1
        mockMvc.perform(put("/api/users/watch-progress/ep_ori_1")
                        .header(HttpHeaders.AUTHORIZATION, memberAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"youtube:OriPlay0001\",\"expectedVersion\":-1,\"positionMs\":570000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));

        mockMvc.perform(get("/api/users/next-episode").param("seriesId", "ser_ori_test")
                        .header(HttpHeaders.AUTHORIZATION, memberAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.episodeId").value("ep_ori_s2_1"))
                .andExpect(jsonPath("$.data.seasonNumber").value(2))
                .andExpect(jsonPath("$.data.episodeNumber").value(1))
                .andExpect(jsonPath("$.data.resumePositionMs").value(0))
                .andExpect(jsonPath("$.data.reason").value("NEXT_UNWATCHED"));

        // Xem xong luôn Mùa 2 Tập 1 -> báo đã xem hết bộ phim, không bị kẹt ở tập youtube_watch
        mockMvc.perform(put("/api/users/watch-progress/ep_ori_s2_1")
                        .header(HttpHeaders.AUTHORIZATION, memberAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"youtube:OriPlayS201\",\"expectedVersion\":-1,\"positionMs\":600000}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/next-episode").param("seriesId", "ser_ori_test")
                        .header(HttpHeaders.AUTHORIZATION, memberAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.message").value("Bạn đã xem hết bộ phim này."));

        // Admin sau khi xem xong tập 1 phải bỏ qua tập 2 (watch-only) và tập 3 (disabled), gợi ý đúng tập 4 (private)
        mockMvc.perform(put("/api/users/watch-progress/ep_ori_1")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"youtube:OriPlay0001\",\"expectedVersion\":-1,\"positionMs\":600000}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/next-episode").param("seriesId", "ser_ori_test")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.episodeId").value("ep_ori_4_private_file"))
                .andExpect(jsonPath("$.data.reason").value("NEXT_UNWATCHED"));
    }

    @Test
    void doesNotLeakProgressOfAssetThatBecamePrivateOrUnavailable() throws Exception {
        seriesRepository.saveAndFlush(new CatalogSeries(
                "ser_access", "Access Series", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, READY, null, 1
        ));
        seasonRepository.saveAndFlush(new CatalogSeason(
                "sea_access_1", "ser_access", 1, "Mùa 1", null, "regular", null,
                null, null, null, READY, null, 1
        ));
        episodeRepository.saveAllAndFlush(List.of(
                episode("ep_acc_1", "ser_access", "sea_access_1", 1, 600_000L, null),
                episode("ep_acc_2", "ser_access", "sea_access_1", 2, 600_000L,
                        "{\"external_ids\":[{\"namespace\":\"youtube_playback\",\"value\":\"AccPlay0002\"}]}")
        ));
        seedFileAsset("ast_acc_1", "ep_acc_1", "public", true, true);
        catalogReadModelCache.invalidate();

        mockMvc.perform(put("/api/users/watch-progress/ep_acc_1")
                        .header(HttpHeaders.AUTHORIZATION, memberAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"ast_acc_1\",\"expectedVersion\":-1,\"positionMs\":120000}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/next-episode").param("seriesId", "ser_access")
                        .header(HttpHeaders.AUTHORIZATION, memberAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.episodeId").value("ep_acc_1"))
                .andExpect(jsonPath("$.data.assetId").value("ast_acc_1"))
                .andExpect(jsonPath("$.data.reason").value("RESUME"));

        // Đổi asset của tập 1 sang private mà KHÔNG cần chờ cache (mô phỏng quyền bị thu hồi trước khi cache kịp nạp lại)
        CatalogMediaAsset existing = catalogMediaAssetRepository.findById("ast_acc_1").orElseThrow();
        existing.applyImport(new CatalogMediaAsset(
                "ast_acc_1", "video", "feature", null, null, null,
                "local", "local_main", "synthetic/acc1.mp4", null, null, 1024L, null, 600_000L,
                null, "[{\"episode_id\":\"ep_acc_1\",\"scope\":\"full_episode\"}]", null,
                "private", true, READY, null, 2
        ));
        catalogMediaAssetRepository.saveAndFlush(existing);

        // Cả watch-progress lẫn next-episode đều không được lộ ast_acc_1 đang private nữa
        mockMvc.perform(get("/api/users/watch-progress").header(HttpHeaders.AUTHORIZATION, memberAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/users/next-episode").param("seriesId", "ser_access")
                        .header(HttpHeaders.AUTHORIZATION, memberAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason").value("NEXT_UNWATCHED"))
                .andExpect(jsonPath("$.data.assetId").doesNotExist());

        // Sau khi cache nạp lại, tập 1 biến mất hẳn khỏi ứng viên của member và next-episode gợi ý thẳng tập 2
        catalogReadModelCache.invalidate();
        mockMvc.perform(get("/api/users/next-episode").param("seriesId", "ser_access")
                        .header(HttpHeaders.AUTHORIZATION, memberAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.episodeId").value("ep_acc_2"))
                .andExpect(jsonPath("$.data.reason").value("NEXT_UNWATCHED"));
    }

    @Test
    void sameSecondProgressPicksMostRecentlySavedEpisodeForResume() throws Exception {
        seriesRepository.saveAndFlush(new CatalogSeries(
                "ser_tie", "Tie Series", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, READY, null, 1
        ));
        seasonRepository.saveAndFlush(new CatalogSeason(
                "sea_tie_1", "ser_tie", 1, "Mùa 1", null, "regular", null,
                null, null, null, READY, null, 1
        ));
        episodeRepository.saveAllAndFlush(List.of(
                episode("ep_tie_209", "ser_tie", "sea_tie_1", 209, 600_000L,
                        "{\"external_ids\":[{\"namespace\":\"youtube_playback\",\"value\":\"TiePlay0209\"}]}"),
                episode("ep_tie_210", "ser_tie", "sea_tie_1", 210, 600_000L,
                        "{\"external_ids\":[{\"namespace\":\"youtube_playback\",\"value\":\"TiePlay0210\"}]}")
        ));
        catalogReadModelCache.invalidate();

        LocalDateTime sameSecond = LocalDateTime.of(2026, 11, 26, 0, 33, 45);
        watchProgressRepository.saveAndFlush(WatchProgress.startAt(
                member.getId(), "ep_tie_209", "youtube:TiePlay0209", 180_000L, sameSecond
        ));
        watchProgressRepository.saveAndFlush(WatchProgress.startAt(
                member.getId(), "ep_tie_210", "youtube:TiePlay0210", 42_000L, sameSecond
        ));

        mockMvc.perform(get("/api/users/next-episode").param("seriesId", "ser_tie")
                        .header(HttpHeaders.AUTHORIZATION, memberAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.episodeId").value("ep_tie_210"))
                .andExpect(jsonPath("$.data.resumePositionMs").value(42_000))
                .andExpect(jsonPath("$.data.assetId").value("youtube:TiePlay0210"))
                .andExpect(jsonPath("$.data.reason").value("RESUME"));
    }

    private static CatalogEpisode episode(String id, String seriesId, String seasonId, int number, Long durationMs,
            String rawJson) {
        return new CatalogEpisode(
                id, seriesId, seasonId, number, "Tập " + number, null, null, null,
                "regular", null, durationMs, null, null, null, null, null, null, null,
                null, null, null, null, null, READY, rawJson, 1
        );
    }

    private void seedFileAsset(String assetId, String episodeId, String policy, boolean enabled, boolean available) {
        catalogMediaAssetRepository.saveAndFlush(new CatalogMediaAsset(
                assetId, "video", "feature", null, null, null,
                "local", "local_main", "synthetic/" + assetId + ".mp4", null, null, 1024L, null, 600_000L,
                null, "[{\"episode_id\":\"" + episodeId + "\",\"scope\":\"full_episode\"}]", null,
                policy, enabled, READY, null, 1
        ));
        mediaAssetRepository.saveAndFlush(new MediaAsset(
                assetId, "next-ep-anchor", "feature", "local", "local_main",
                "synthetic/" + assetId + ".mp4", "v1-" + assetId, "video/mp4", 1024L, 600.0,
                "public".equals(policy), available
        ));
    }
}
