package com.dippie.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogEpisodeRepository;
import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.catalog.domain.CatalogMediaAssetRepository;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.domain.CatalogSeriesRepository;
import com.dippie.library.MovieRating;
import com.dippie.library.MovieRatingRepository;
import com.dippie.movie.Movie;
import com.dippie.movie.MovieRepository;
import com.dippie.progress.CompletionPolicy;
import com.dippie.progress.WatchDuration;
import com.dippie.progress.WatchProgress;
import com.dippie.progress.WatchProgressRepository;
import com.dippie.security.JwtService;
import com.dippie.security.Role;
import com.dippie.user.User;
import com.dippie.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AdminStatsControllerTest {

    private static final String READY_RECORD = "{\"status\":\"ready\"}";
    private static final String DRAFT_RECORD = "{\"status\":\"draft\"}";

    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final MovieRepository movieRepository;
    private final MovieRatingRepository movieRatingRepository;
    private final CatalogSeriesRepository seriesRepository;
    private final CatalogEpisodeRepository episodeRepository;
    private final CatalogMediaAssetRepository catalogMediaAssetRepository;
    private final WatchProgressRepository watchProgressRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    AdminStatsControllerTest(
            MockMvc mockMvc,
            UserRepository userRepository,
            MovieRepository movieRepository,
            MovieRatingRepository movieRatingRepository,
            CatalogSeriesRepository seriesRepository,
            CatalogEpisodeRepository episodeRepository,
            CatalogMediaAssetRepository catalogMediaAssetRepository,
            WatchProgressRepository watchProgressRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            Clock clock
    ) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.movieRepository = movieRepository;
        this.movieRatingRepository = movieRatingRepository;
        this.seriesRepository = seriesRepository;
        this.episodeRepository = episodeRepository;
        this.catalogMediaAssetRepository = catalogMediaAssetRepository;
        this.watchProgressRepository = watchProgressRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    @BeforeEach
    void setUp() {
        watchProgressRepository.deleteAll();
        movieRatingRepository.deleteAll();
        catalogMediaAssetRepository.deleteAll();
        episodeRepository.deleteAll();
        seriesRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void computesAggregatedStatsWithCompletionPolicyParity() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        User admin = saveUser("admin", "admin@dippie.com", Role.ROLE_ADMIN, now.minusDays(20), true);
        User recentUser1 = saveUser("u1", "u1@dippie.com", Role.ROLE_USER, now.minusDays(2), true);
        User recentUser2 = saveUser("u2", "u2@dippie.com", Role.ROLE_USER, now.minusDays(1), false);
        User oldUser = saveUser("u3", "u3@dippie.com", Role.ROLE_USER, now.minusDays(10), true);

        movieRepository.saveAndFlush(new Movie("mov-a", "Movie A", 2024, 8.0, "90m", "[\"Animation\"]", "Desc", null, null, null));
        movieRepository.saveAndFlush(new Movie("mov-b", "Movie B", 2024, 7.5, "90m", "[\"Animation\"]", "Desc", null, null, null));
        movieRepository.saveAndFlush(new Movie("mov-c", "Movie C", 2024, 6.0, "90m", "[\"Animation\"]", "Desc", null, null, null));
        movieRepository.saveAndFlush(new Movie("mov-unrated", "Unrated Movie", 2024, 5.0, "90m", "[\"Animation\"]", "Desc", null, null, null));

        movieRatingRepository.saveAndFlush(new MovieRating(recentUser1.getId(), "mov-a", 8, now));
        movieRatingRepository.saveAndFlush(new MovieRating(recentUser2.getId(), "mov-a", 9, now));
        movieRatingRepository.saveAndFlush(new MovieRating(recentUser1.getId(), "mov-b", 9, now));
        movieRatingRepository.saveAndFlush(new MovieRating(recentUser2.getId(), "mov-b", 8, now));
        movieRatingRepository.saveAndFlush(new MovieRating(oldUser.getId(), "mov-b", 8, now));
        movieRatingRepository.saveAndFlush(new MovieRating(recentUser1.getId(), "mov-c", 9, now));

        seriesRepository.saveAndFlush(new CatalogSeries(
                "ser-1", "Series 1", null, null, null, null, null, null, null, null, null,
                2024, 2024, "ongoing", null, null, null, null, null, null, null, READY_RECORD, "{}", 1
        ));

        CatalogEpisode epLong = saveEpisode("ep-1", "ser-1", 1, "Episode Long", 1_500_000L, READY_RECORD);
        CatalogEpisode epShort = saveEpisode("ep-2", "ser-1", 2, "Episode Short", 100_000L, READY_RECORD);
        saveEpisode("ep-3", "ser-1", 3, "Episode Draft", 100_000L, DRAFT_RECORD);

        CatalogMediaAsset assetLong = saveAsset("asset-1", 1_500_000L);
        CatalogMediaAsset assetShort = saveAsset("asset-2", 90_000L);

        long effectiveLong = WatchDuration.resolve(epLong.getDurationMs(), assetLong.getDurationMs()).durationMs();
        long effectiveShort = WatchDuration.resolve(epShort.getDurationMs(), assetShort.getDurationMs()).durationMs();

        assertThat(CompletionPolicy.isCompleted(1_440_000L, effectiveLong)).isTrue();
        assertThat(CompletionPolicy.isCompleted(1_439_999L, effectiveLong)).isFalse();
        assertThat(CompletionPolicy.isCompleted(85_500L, effectiveShort)).isTrue();
        assertThat(CompletionPolicy.isCompleted(85_499L, effectiveShort)).isFalse();

        watchProgressRepository.saveAndFlush(WatchProgress.startAt(recentUser1.getId(), "ep-1", "asset-1", 1_440_000L, now));
        watchProgressRepository.saveAndFlush(WatchProgress.startAt(recentUser2.getId(), "ep-1", "asset-1", 1_439_999L, now));
        watchProgressRepository.saveAndFlush(WatchProgress.startAt(oldUser.getId(), "ep-2", "asset-2", 85_500L, now));
        watchProgressRepository.saveAndFlush(WatchProgress.startAt(admin.getId(), "ep-2", "asset-2", 85_499L, now));
        watchProgressRepository.saveAndFlush(WatchProgress.startAt(recentUser1.getId(), "ep-2", "asset-2", 89_000L, now));

        String adminAuth = "Bearer " + jwtService.issueLoginToken(admin.getUsername(), admin.getTokenVersion());

        mockMvc.perform(get("/api/admin/stats")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalUsers").value(4))
                .andExpect(jsonPath("$.data.activeUsers").value(3))
                .andExpect(jsonPath("$.data.totalMovies").value(4))
                .andExpect(jsonPath("$.data.publishedEpisodes").value(2))
                .andExpect(jsonPath("$.data.totalWatchSessions").value(5))
                .andExpect(jsonPath("$.data.completedWatchSessions").value(3))
                .andExpect(jsonPath("$.data.completionRate").value(0.6))
                .andExpect(jsonPath("$.data.newUsersLast7Days").value(2))
                .andExpect(jsonPath("$.data.topWatchedEpisodes.length()").value(2))
                .andExpect(jsonPath("$.data.topWatchedEpisodes[0].episodeId").value("ep-2"))
                .andExpect(jsonPath("$.data.topWatchedEpisodes[0].title").value("Episode Short"))
                .andExpect(jsonPath("$.data.topWatchedEpisodes[0].viewers").value(3))
                .andExpect(jsonPath("$.data.topWatchedEpisodes[1].episodeId").value("ep-1"))
                .andExpect(jsonPath("$.data.topWatchedEpisodes[1].viewers").value(2))
                .andExpect(jsonPath("$.data.topRatedMovies.length()").value(3))
                .andExpect(jsonPath("$.data.topRatedMovies[0].movieId").value("mov-c"))
                .andExpect(jsonPath("$.data.topRatedMovies[0].averageRating").value(9.0))
                .andExpect(jsonPath("$.data.topRatedMovies[0].ratingCount").value(1))
                .andExpect(jsonPath("$.data.topRatedMovies[1].movieId").value("mov-a"))
                .andExpect(jsonPath("$.data.topRatedMovies[1].averageRating").value(8.5))
                .andExpect(jsonPath("$.data.topRatedMovies[1].ratingCount").value(2))
                .andExpect(jsonPath("$.data.topRatedMovies[2].movieId").value("mov-b"))
                .andExpect(jsonPath("$.data.topRatedMovies[2].ratingCount").value(3));
    }

    @Test
    void returnsZeroCompletionRateWhenNoWatchSessionsExist() throws Exception {
        User admin = saveUser("admin", "admin@dippie.com", Role.ROLE_ADMIN, LocalDateTime.now(clock), true);
        String adminAuth = "Bearer " + jwtService.issueLoginToken(admin.getUsername(), admin.getTokenVersion());

        mockMvc.perform(get("/api/admin/stats")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(1))
                .andExpect(jsonPath("$.data.totalWatchSessions").value(0))
                .andExpect(jsonPath("$.data.completedWatchSessions").value(0))
                .andExpect(jsonPath("$.data.completionRate").value(0.0));
    }

    private User saveUser(String username, String email, Role role, LocalDateTime createdAt, boolean enabled) {
        User user = new User(username, email, passwordEncoder.encode("secret123"), username, role, createdAt);
        if (!enabled) {
            user.disable(createdAt);
        }
        return userRepository.saveAndFlush(user);
    }

    private CatalogEpisode saveEpisode(
            String id,
            String seriesId,
            int number,
            String title,
            Long durationMs,
            String recordJson
    ) {
        return episodeRepository.saveAndFlush(new CatalogEpisode(
                id, seriesId, null, number, title, null, null, null, "regular", null,
                durationMs, null, null, null, null, null, null, null, null, null, null, null, null,
                recordJson, "{}", 1
        ));
    }

    private CatalogMediaAsset saveAsset(String id, Long durationMs) {
        return catalogMediaAssetRepository.saveAndFlush(new CatalogMediaAsset(
                id, "video", "feature", null, null, null, "local", "key-" + id,
                "videos/" + id + ".mp4", null, null, 1024L, null, durationMs,
                null, null, null, "public", true, READY_RECORD, "{}", 1
        ));
    }
}
