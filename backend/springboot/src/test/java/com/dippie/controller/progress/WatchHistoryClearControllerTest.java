package com.dippie.controller.progress;

import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.progress.WatchProgress;
import com.dippie.model.user.Role;
import com.dippie.model.user.User;
import com.dippie.repository.catalog.CatalogEpisodeRepository;
import com.dippie.repository.catalog.CatalogSeriesRepository;
import com.dippie.repository.progress.WatchProgressRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Test tích hợp cho DELETE /api/users/watch-progress: chỉ xóa lịch sử của tài khoản đang đăng nhập và giữ nguyên dữ liệu người khác.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WatchHistoryClearControllerTest {

    private static final String READY = "{\"status\":\"ready\"}";

    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final CatalogSeriesRepository seriesRepository;
    private final CatalogEpisodeRepository episodeRepository;
    private final WatchProgressRepository watchProgressRepository;
    private final JwtService jwtService;

    private User alice;
    private User bob;
    private String aliceAuth;
    private String bobAuth;

    WatchHistoryClearControllerTest(
            MockMvc mockMvc,
            UserRepository userRepository,
            CatalogSeriesRepository seriesRepository,
            CatalogEpisodeRepository episodeRepository,
            WatchProgressRepository watchProgressRepository,
            JwtService jwtService
    ) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.seriesRepository = seriesRepository;
        this.episodeRepository = episodeRepository;
        this.watchProgressRepository = watchProgressRepository;
        this.jwtService = jwtService;
    }

    @BeforeEach
    void setUp() {
        watchProgressRepository.deleteAll();
        episodeRepository.deleteAll();
        seriesRepository.deleteAll();
        userRepository.deleteAll();

        alice = userRepository.saveAndFlush(new User(
                "alice_hist", "alice.hist@example.com", "pw", "Alice", Role.ROLE_USER,
                LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        bob = userRepository.saveAndFlush(new User(
                "bob_hist", "bob.hist@example.com", "pw", "Bob", Role.ROLE_USER,
                LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        aliceAuth = "Bearer " + jwtService.issueLoginToken(alice.getUsername(), alice.getTokenVersion());
        bobAuth = "Bearer " + jwtService.issueLoginToken(bob.getUsername(), bob.getTokenVersion());

        seriesRepository.saveAndFlush(new CatalogSeries(
                "ser_hist", "Doraemon", null, null, null, null, null, null, null, null, null,
                2020, null, null, null, null, null, null, null, null, null, READY, null, 1
        ));
        episodeRepository.saveAndFlush(new CatalogEpisode(
                "ep_hist_1", "ser_hist", null, 1, "Tập 1", null, null, null, "regular", null,
                600_000L, null, null, null, null, null, null, null, null, null, null, null, null, READY, null, 1
        ));
        episodeRepository.saveAndFlush(new CatalogEpisode(
                "ep_hist_2", "ser_hist", null, 2, "Tập 2", null, null, null, "regular", null,
                600_000L, null, null, null, null, null, null, null, null, null, null, null, null, READY, null, 1
        ));
    }

    @Test
    void rejectsAnonymousClearAllWith401() throws Exception {
        mockMvc.perform(delete("/api/users/watch-progress"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập để tiếp tục."));
    }

    // Xóa toàn bộ lịch sử của Alice phải giữ nguyên dữ liệu của Bob, và gọi DELETE lần hai khi đã trống vẫn trả 204.
    @Test
    void clearsOnlyCurrentUserProgressAndRemainsIdempotent() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 1, 2, 10, 0);
        watchProgressRepository.saveAndFlush(WatchProgress.startAt(
                alice.getId(), "ep_hist_1", "youtube:vid1", 12_000L, now
        ));
        watchProgressRepository.saveAndFlush(WatchProgress.startAt(
                alice.getId(), "ep_hist_2", "youtube:vid2", 24_000L, now.plusMinutes(1)
        ));
        watchProgressRepository.saveAndFlush(WatchProgress.startAt(
                bob.getId(), "ep_hist_1", "youtube:vid1", 30_000L, now.plusMinutes(2)
        ));

        mockMvc.perform(delete("/api/users/watch-progress")
                        .header(HttpHeaders.AUTHORIZATION, aliceAuth))
                .andExpect(status().isNoContent());

        assertThat(watchProgressRepository.findByUserIdAndEpisodeId(alice.getId(), "ep_hist_1")).isEmpty();
        assertThat(watchProgressRepository.findByUserIdAndEpisodeId(alice.getId(), "ep_hist_2")).isEmpty();
        assertThat(watchProgressRepository.findByUserIdAndEpisodeId(bob.getId(), "ep_hist_1")).isPresent();

        mockMvc.perform(get("/api/users/watch-progress")
                        .header(HttpHeaders.AUTHORIZATION, aliceAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // Bấm xóa lần hai lúc danh sách đã trống vẫn trả 204 và không đụng tới lịch sử của Bob
        mockMvc.perform(delete("/api/users/watch-progress")
                        .header(HttpHeaders.AUTHORIZATION, aliceAuth))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/watch-progress")
                        .header(HttpHeaders.AUTHORIZATION, bobAuth))
                .andExpect(status().isOk());
        assertThat(watchProgressRepository.findByUserIdAndEpisodeId(bob.getId(), "ep_hist_1")).isPresent();
    }
}
