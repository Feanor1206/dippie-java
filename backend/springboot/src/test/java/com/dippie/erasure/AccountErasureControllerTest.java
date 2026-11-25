package com.dippie.erasure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippie.admin.AdminAuditEvent;
import com.dippie.admin.AdminAuditEventRepository;
import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogEpisodeRepository;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.domain.CatalogSeriesRepository;
import com.dippie.library.FavoriteItem;
import com.dippie.library.FavoriteItemRepository;
import com.dippie.library.MovieRating;
import com.dippie.library.MovieRatingRepository;
import com.dippie.library.WatchlistItem;
import com.dippie.library.WatchlistItemRepository;
import com.dippie.movie.Movie;
import com.dippie.movie.MovieRepository;
import com.dippie.passwordreset.PasswordResetToken;
import com.dippie.passwordreset.PasswordResetTokenRepository;
import com.dippie.progress.WatchProgress;
import com.dippie.progress.WatchProgressRepository;
import com.dippie.report.PlaybackReport;
import com.dippie.report.PlaybackReportReason;
import com.dippie.report.PlaybackReportRepository;
import com.dippie.report.PlaybackReportStatus;
import com.dippie.security.JwtService;
import com.dippie.security.Role;
import com.dippie.user.User;
import com.dippie.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

// Kiểm tra luồng tự xóa tài khoản qua MockMvc: xác nhận mật khẩu, dọn dữ liệu cá nhân, ghi audit và giữ lại admin cuối.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AccountErasureControllerTest {

    private static final String READY = "{\"status\":\"ready\"}";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final MovieRepository movieRepository;
    private final CatalogSeriesRepository seriesRepository;
    private final CatalogEpisodeRepository episodeRepository;
    private final MovieRatingRepository movieRatingRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final FavoriteItemRepository favoriteItemRepository;
    private final WatchProgressRepository watchProgressRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PlaybackReportRepository playbackReportRepository;
    private final AdminAuditEventRepository auditEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    AccountErasureControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            MovieRepository movieRepository,
            CatalogSeriesRepository seriesRepository,
            CatalogEpisodeRepository episodeRepository,
            MovieRatingRepository movieRatingRepository,
            WatchlistItemRepository watchlistItemRepository,
            FavoriteItemRepository favoriteItemRepository,
            WatchProgressRepository watchProgressRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PlaybackReportRepository playbackReportRepository,
            AdminAuditEventRepository auditEventRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.movieRepository = movieRepository;
        this.seriesRepository = seriesRepository;
        this.episodeRepository = episodeRepository;
        this.movieRatingRepository = movieRatingRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.favoriteItemRepository = favoriteItemRepository;
        this.watchProgressRepository = watchProgressRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.playbackReportRepository = playbackReportRepository;
        this.auditEventRepository = auditEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @BeforeEach
    void setUp() {
        playbackReportRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        watchProgressRepository.deleteAll();
        movieRatingRepository.deleteAll();
        watchlistItemRepository.deleteAll();
        favoriteItemRepository.deleteAll();
        auditEventRepository.deleteAll();
        episodeRepository.deleteAll();
        seriesRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.deleteAll();

        movieRepository.saveAndFlush(new Movie(
                "ep_erase_1", "Tập kiểm thử", 2024, 8.5, "10m", "Hài", null, null, null, null
        ));
        seriesRepository.saveAndFlush(new CatalogSeries(
                "ser_erase", "Bộ kiểm thử", null, null, null, null, null, null, null, null, null,
                2024, null, null, null, null, null, null, null, null, null, READY, null, 1
        ));
        episodeRepository.saveAndFlush(new CatalogEpisode(
                "ep_erase_1", "ser_erase", null, 1, "Tập 1", null, null, null, "regular", null,
                600_000L, null, null, null, null, null, null, null, null, null, null, null, null, READY, null, 1
        ));
    }

    // Gọi xóa khi chưa đăng nhập, bỏ trống mật khẩu hoặc nhập sai mật khẩu đều phải bị chặn.
    @Test
    void rejectsAnonymousMissingPasswordAndWrongPassword() throws Exception {
        User viewer = saveUser("viewer", "viewer@example.com", Role.ROLE_USER);
        String bearer = bearerFor(viewer);

        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secret123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bạn cần đăng nhập để tiếp tục."));

        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AccountErasureService.MISSING_PASSWORD_MESSAGE));

        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "wrongpass"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AccountErasureService.WRONG_PASSWORD_MESSAGE));

        assertThat(userRepository.findById(viewer.getId())).isPresent();
    }

    // Xóa sạch mọi bảng cá nhân của Alice, không đụng dữ liệu Bob, gỡ liên kết report và ghi dòng audit ACCOUNT_DELETED.
    @Test
    void deletesAllPersonalRowsWritesAuditAndPreservesOtherUsers() throws Exception {
        User alice = saveUser("alice", "alice@example.com", Role.ROLE_USER);
        User bob = saveUser("bob", "bob@example.com", Role.ROLE_USER);
        String aliceBearer = bearerFor(alice);
        LocalDateTime now = LocalDateTime.of(2026, 2, 1, 12, 0);

        // Tạo dữ liệu ở toàn bộ bảng phụ cho cả Alice và Bob để đối chiếu sau khi xóa.
        movieRatingRepository.saveAndFlush(new MovieRating(alice.getId(), "ep_erase_1", 9, now));
        movieRatingRepository.saveAndFlush(new MovieRating(bob.getId(), "ep_erase_1", 7, now));
        watchlistItemRepository.saveAndFlush(new WatchlistItem(alice.getId(), "ep_erase_1", now));
        watchlistItemRepository.saveAndFlush(new WatchlistItem(bob.getId(), "ep_erase_1", now));
        favoriteItemRepository.saveAndFlush(new FavoriteItem(alice.getId(), "ep_erase_1", now));
        favoriteItemRepository.saveAndFlush(new FavoriteItem(bob.getId(), "ep_erase_1", now));
        watchProgressRepository.saveAndFlush(WatchProgress.startAt(
                alice.getId(), "ep_erase_1", "youtube:abc", 45_000L, now
        ));
        watchProgressRepository.saveAndFlush(WatchProgress.startAt(
                bob.getId(), "ep_erase_1", "youtube:abc", 60_000L, now
        ));
        passwordResetTokenRepository.saveAndFlush(new PasswordResetToken(
                alice.getId(), "a".repeat(64), now.plusMinutes(30), now
        ));
        PlaybackReport aliceReport = playbackReportRepository.saveAndFlush(new PlaybackReport(
                "ep_erase_1", "youtube:abc", alice.getId(), PlaybackReportReason.KHONG_PHAT, "Lỗi phát", now
        ));

        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, aliceBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "secret123"))))
                .andExpect(status().isNoContent());

        // Dữ liệu cá nhân của Alice biến mất hết; riêng report vẫn giữ lại nhưng reporterUserId về null.
        assertThat(userRepository.findById(alice.getId())).isEmpty();
        assertThat(movieRatingRepository.findByUserIdOrderByUpdatedAtDesc(alice.getId())).isEmpty();
        assertThat(watchlistItemRepository.findByUserIdAndMovieId(alice.getId(), "ep_erase_1")).isEmpty();
        assertThat(favoriteItemRepository.findByUserIdAndMovieId(alice.getId(), "ep_erase_1")).isEmpty();
        assertThat(watchProgressRepository.findByUserIdAndEpisodeId(alice.getId(), "ep_erase_1")).isEmpty();
        assertThat(passwordResetTokenRepository.findByUserId(alice.getId())).isEmpty();
        assertThat(playbackReportRepository.findById(aliceReport.getId()).orElseThrow().getReporterUserId()).isNull();

        // Dữ liệu của Bob phải còn nguyên vẹn.
        assertThat(userRepository.findById(bob.getId())).isPresent();
        assertThat(movieRatingRepository.findByUserIdAndMovieId(bob.getId(), "ep_erase_1")).isPresent();
        assertThat(watchlistItemRepository.findByUserIdAndMovieId(bob.getId(), "ep_erase_1")).isPresent();
        assertThat(favoriteItemRepository.findByUserIdAndMovieId(bob.getId(), "ep_erase_1")).isPresent();
        assertThat(watchProgressRepository.findByUserIdAndEpisodeId(bob.getId(), "ep_erase_1")).isPresent();

        // JWT cũ của Alice phải bị từ chối ngay sau khi tài khoản không còn.
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, aliceBearer))
                .andExpect(status().isUnauthorized());

        // Bảng audit vẫn giữ tên tài khoản đã xóa dù dòng user không còn trong DB.
        List<AdminAuditEvent> audits = auditEventRepository.findAll();
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().getAction()).isEqualTo(AccountErasureService.ACTION_ACCOUNT_DELETED);
        assertThat(audits.getFirst().getActorUsername()).isEqualTo("alice");
        assertThat(audits.getFirst().getTargetType()).isEqualTo("USER");
        assertThat(audits.getFirst().getTargetId()).isEqualTo(String.valueOf(alice.getId()));
    }

    // Chặn admin hoạt động cuối cùng tự xóa (kể cả khi còn admin bị khóa), nhưng cho xóa khi có admin hoạt động thứ hai.
    @Test
    void protectsLastEnabledAdminAndAllowsDeletingOneOfTwoAdmins() throws Exception {
        User admin1 = saveUser("admin1", "admin1@dippie.com", Role.ROLE_ADMIN);
        User disabledAdmin = saveUser("admin_off", "admin.off@dippie.com", Role.ROLE_ADMIN);
        disabledAdmin.disable();
        userRepository.saveAndFlush(disabledAdmin);

        // admin_off đã bị khóa nên không tính, admin1 là người duy nhất còn bật nên phải trả 409.
        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(admin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secret123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(AccountErasureService.LAST_ADMIN_MESSAGE));

        assertThat(userRepository.findById(admin1.getId())).isPresent();

        // Có thêm admin2 đang hoạt động thì admin1 xóa được bình thường, kể cả khi đã có dòng audit và report gắn với mình.
        User admin2 = saveUser("admin2", "admin2@dippie.com", Role.ROLE_ADMIN);
        LocalDateTime now = LocalDateTime.of(2026, 2, 1, 12, 0);
        auditEventRepository.saveAndFlush(new AdminAuditEvent(
                admin1.getId(), admin1.getUsername(), "USER_ENABLED", "USER", "99", "{}", now
        ));
        PlaybackReport resolved = new PlaybackReport(
                "ep_erase_1", null, admin1.getId(), PlaybackReportReason.KHAC, "Kiểm thử", now
        );
        resolved.updateResolution(PlaybackReportStatus.RESOLVED, admin1.getId(), now);
        playbackReportRepository.saveAndFlush(resolved);

        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(admin1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secret123\"}"))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(admin1.getId())).isEmpty();
        assertThat(userRepository.findById(admin2.getId())).isPresent();
    }

    // Hai admin bấm tự xóa đúng cùng một thời điểm: khóa ghi phải chặn được một người (409) để luôn còn 1 admin sống.
    @Test
    void concurrentSelfDeletionByTwoAdminsAlwaysLeavesOneEnabledAdmin() throws Exception {
        User admin1 = saveUser("race_admin1", "race1@dippie.com", Role.ROLE_ADMIN);
        User admin2 = saveUser("race_admin2", "race2@dippie.com", Role.ROLE_ADMIN);
        String firstBearer = bearerFor(admin1);
        String secondBearer = bearerFor(admin2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> firstDelete = () -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(delete("/api/users/me")
                                .header(HttpHeaders.AUTHORIZATION, firstBearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"secret123\"}"))
                        .andReturn().getResponse().getStatus();
            };
            Callable<Integer> secondDelete = () -> {
                ready.countDown();
                start.await();
                return mockMvc.perform(delete("/api/users/me")
                                .header(HttpHeaders.AUTHORIZATION, secondBearer)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"secret123\"}"))
                        .andReturn().getResponse().getStatus();
            };

            Future<Integer> firstStatus = executor.submit(firstDelete);
            Future<Integer> secondStatus = executor.submit(secondDelete);
            ready.await();
            start.countDown();

            List<Integer> statuses = List.of(firstStatus.get(), secondStatus.get()).stream().sorted().toList();
            assertThat(statuses).containsExactly(204, 409);
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
                LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
    }

    private String bearerFor(User user) {
        return "Bearer " + jwtService.issueLoginToken(user.getUsername(), user.getTokenVersion());
    }
}
