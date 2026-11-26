package com.dippie.controller.vault;

import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogEpisodeAsset;
import com.dippie.model.catalog.CatalogMediaAsset;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.media.MediaAsset;
import com.dippie.model.movie.Movie;
import com.dippie.model.profile.ScreenTimeLedgerEntry;
import com.dippie.model.profile.ScreenTimeLedgerKind;
import com.dippie.repository.catalog.CatalogEpisodeRepository;
import com.dippie.repository.catalog.CatalogMediaAssetRepository;
import com.dippie.repository.catalog.CatalogSeriesRepository;
import com.dippie.repository.media.MediaAssetRepository;
import com.dippie.repository.movie.MovieRepository;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.profile.ScreenTimeLedgerRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.repository.vault.WeekendVaultOverrideRepository;
import com.dippie.security.LoginRateLimiter;
import com.dippie.service.catalog.CatalogReadModelCache;
import com.dippie.service.profile.ParentPinRateLimiter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// test két sắt phim cuối tuần: khóa phim lẻ T2-T6, cộng Vé xem phim từ sổ cái, mở T7-CN ở 100%, reset thứ Hai và mở thủ công bằng PIN
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(WeekendVaultControllerTest.ClockOverrideConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WeekendVaultControllerTest {

    private static final String READY_JSON = "{\"status\":\"ready\"}";

    @TempDir
    static Path mediaRoot;

    @DynamicPropertySource
    static void registerMediaRoot(DynamicPropertyRegistry registry) {
        registry.add("dippie.media.root", () -> mediaRoot.toString());
    }

    @TestConfiguration
    static class ClockOverrideConfig {

        @Bean
        @Primary
        MutableClock vaultMutableClock() {
            // thứ Hai 2026-11-23 12:00+07:00
            return new MutableClock(Instant.parse("2026-11-23T05:00:00Z"));
        }
    }

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final MutableClock clock;
    private final UserRepository userRepository;
    private final ChildProfileRepository childProfileRepository;
    private final ScreenTimeLedgerRepository ledgerRepository;
    private final WeekendVaultOverrideRepository overrideRepository;
    private final MovieRepository movieRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final CatalogMediaAssetRepository catalogMediaAssetRepository;
    private final CatalogSeriesRepository seriesRepository;
    private final CatalogEpisodeRepository episodeRepository;
    private final EntityManager entityManager;
    private final TransactionTemplate writes;
    private final CatalogReadModelCache catalogReadModelCache;
    private final LoginRateLimiter loginRateLimiter;
    private final ParentPinRateLimiter parentPinRateLimiter;

    WeekendVaultControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            MutableClock clock,
            UserRepository userRepository,
            ChildProfileRepository childProfileRepository,
            ScreenTimeLedgerRepository ledgerRepository,
            WeekendVaultOverrideRepository overrideRepository,
            MovieRepository movieRepository,
            MediaAssetRepository mediaAssetRepository,
            CatalogMediaAssetRepository catalogMediaAssetRepository,
            CatalogSeriesRepository seriesRepository,
            CatalogEpisodeRepository episodeRepository,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager,
            CatalogReadModelCache catalogReadModelCache,
            LoginRateLimiter loginRateLimiter,
            ParentPinRateLimiter parentPinRateLimiter
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.userRepository = userRepository;
        this.childProfileRepository = childProfileRepository;
        this.ledgerRepository = ledgerRepository;
        this.overrideRepository = overrideRepository;
        this.movieRepository = movieRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.catalogMediaAssetRepository = catalogMediaAssetRepository;
        this.seriesRepository = seriesRepository;
        this.episodeRepository = episodeRepository;
        this.entityManager = entityManager;
        this.writes = new TransactionTemplate(transactionManager);
        this.catalogReadModelCache = catalogReadModelCache;
        this.loginRateLimiter = loginRateLimiter;
        this.parentPinRateLimiter = parentPinRateLimiter;
    }

    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.parse("2026-11-23T05:00:00Z"));
        loginRateLimiter.reset();
        parentPinRateLimiter.reset();
        overrideRepository.deleteAll();
        ledgerRepository.deleteAll();
        childProfileRepository.deleteAll();
        writes.executeWithoutResult(status -> entityManager.createQuery("delete from CatalogEpisodeAsset").executeUpdate());
        episodeRepository.deleteAll();
        seriesRepository.deleteAll();
        catalogMediaAssetRepository.deleteAll();
        mediaAssetRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.deleteAll();
        catalogReadModelCache.invalidate();
        Files.write(mediaRoot.resolve("solo-film.mp4"), new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        Files.write(mediaRoot.resolve("series-ep1.mp4"), new byte[]{9, 8, 7, 6, 5, 4, 3, 2});
        seedStandaloneFilmAndSeriesEpisode();
    }

    // test phim lẻ bị khóa 423 WEEKEND_VAULT từ T2-T6 cho bé, phim bộ và người lớn/khách vẫn phát bình thường, đủ 100% mở vào T7-CN và reset T2
    @Test
    void enforcesWeekdayLockCalculatesWeeklyTicketProgressAndUnlocksOnWeekendAtHundredPercent() throws Exception {
        String mainToken = registerUser("vaultmom", "vaultmom@example.com", "secret123", "Mẹ Vy");
        String soloToken = registerUser("adultsolo", "adultsolo@example.com", "secret123", "Adult Solo");
        enableParentMode(mainToken, "5678");
        long child1Id = createChildProfile(mainToken, "5678", "Bé Bắp", "dino", 10);
        long child2Id = createChildProfile(mainToken, "5678", "Bé Mít", "cat", 10);
        String child1Token = switchToChild(mainToken, child1Id);
        String child2Token = switchToChild(mainToken, child2Id);

        // thứ Hai: phim lẻ khóa 423 WEEKEND_VAULT với bé 1, còn tập phim bộ và người lớn/khách vẫn 200 OK
        mockMvc.perform(post("/api/media/grant/asset_solo_1").header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "WEEKEND_VAULT"));
        mockMvc.perform(get("/api/media/stream/asset_solo_1").header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "WEEKEND_VAULT"));
        mockMvc.perform(post("/api/media/grant/asset_reg_ep1").header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isOk());

        for (String adult : List.of(mainToken, soloToken)) {
            mockMvc.perform(post("/api/media/grant/asset_solo_1").header(HttpHeaders.AUTHORIZATION, bearer(adult)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/users/weekend-vault").header(HttpHeaders.AUTHORIZATION, bearer(adult)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.enabled").value(false))
                    .andExpect(jsonPath("$.data.locked").value(false));
        }
        mockMvc.perform(get("/api/media/stream/asset_solo_1")).andExpect(status().isOk());

        // thứ Hai 2026-11-23: bé 1 xem 120s (còn dư > 0) và làm đủ 3 câu thưởng toán -> trong ngày T2 mới tính +20% toán (chưa hết ngày)
        addLedger(child1Id, LocalDate.of(2026, 11, 23), ScreenTimeLedgerKind.WATCH, 120);
        addLedger(child1Id, LocalDate.of(2026, 11, 23), ScreenTimeLedgerKind.REWARD, 300);
        addLedger(child1Id, LocalDate.of(2026, 11, 23), ScreenTimeLedgerKind.REWARD, 300);
        addLedger(child1Id, LocalDate.of(2026, 11, 23), ScreenTimeLedgerKind.REWARD, 300);

        mockMvc.perform(get("/api/users/weekend-vault").header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressPercent").value(20))
                .andExpect(jsonPath("$.data.locked").value(true));

        // thứ Ba 2026-11-24: thứ Hai đã hết ngày nên giờ thành 40%; thứ Ba bé 1 xem sạch 600s (remaining == 0) nên hôm sau không được +20%
        clock.set(Instant.parse("2026-11-24T05:00:00Z"));
        addLedger(child1Id, LocalDate.of(2026, 11, 24), ScreenTimeLedgerKind.WATCH, 600);
        mockMvc.perform(get("/api/users/weekend-vault").header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressPercent").value(40))
                .andExpect(jsonPath("$.data.days[0].stoppedInTime").value(true))
                .andExpect(jsonPath("$.data.days[0].mathGoalMet").value(true));

        // thứ Tư, thứ Năm, thứ Sáu: mỗi ngày xem 60s rồi dừng sớm -> mỗi ngày +20% -> tổng 40 + 60 = 100%
        addLedger(child1Id, LocalDate.of(2026, 11, 25), ScreenTimeLedgerKind.WATCH, 60);
        addLedger(child1Id, LocalDate.of(2026, 11, 26), ScreenTimeLedgerKind.WATCH, 60);
        addLedger(child1Id, LocalDate.of(2026, 11, 27), ScreenTimeLedgerKind.WATCH, 60);

        // thứ Sáu tối: làm mới JWT để khỏi hết hạn 24h sau khi tua nhiều ngày, dù đạt 80% vẫn khóa vì chưa tới cuối tuần
        clock.set(Instant.parse("2026-11-27T12:00:00Z"));
        String mainTokenFri = loginUser("vaultmom", "secret123");
        String child1TokenFri = switchToChild(mainTokenFri, child1Id);
        mockMvc.perform(get("/api/users/weekend-vault").header(HttpHeaders.AUTHORIZATION, bearer(child1TokenFri)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressPercent").value(80))
                .andExpect(jsonPath("$.data.locked").value(true));

        // Chủ Nhật tối 2026-11-29 23:58+07:00 (2026-11-29T16:58:00Z): bé 1 đạt 100% nên phim lẻ tự mở, bé 2 ở 0% vẫn bị khóa 423
        clock.set(Instant.parse("2026-11-29T16:58:00Z"));
        String mainTokenSun = loginUser("vaultmom", "secret123");
        String child1TokenSun = switchToChild(mainTokenSun, child1Id);
        String child2TokenSun = switchToChild(mainTokenSun, child2Id);
        mockMvc.perform(get("/api/users/weekend-vault").header(HttpHeaders.AUTHORIZATION, bearer(child1TokenSun)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weekend").value(true))
                .andExpect(jsonPath("$.data.progressPercent").value(100))
                .andExpect(jsonPath("$.data.unlocked").value(true))
                .andExpect(jsonPath("$.data.locked").value(false))
                .andExpect(jsonPath("$.data.days[1].stoppedInTime").value(false));

        MvcResult grantRes = mockMvc.perform(post("/api/media/grant/asset_solo_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1TokenSun)))
                .andExpect(status().isOk())
                .andReturn();
        String grantUrl = objectMapper.readTree(grantRes.getResponse().getContentAsString()).path("streamUrl").asText();
        mockMvc.perform(get(grantUrl)).andExpect(status().isOk());

        mockMvc.perform(post("/api/media/grant/asset_solo_1").header(HttpHeaders.AUTHORIZATION, bearer(child2TokenSun)))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "WEEKEND_VAULT"));

        // sang thứ Hai tuần mới 2026-11-30 00:00+07:00 (2026-11-29T17:00:00Z): reset về 0% và khóa lại cả vé phát cũ còn hạn
        clock.set(Instant.parse("2026-11-29T17:00:00Z"));
        mockMvc.perform(get("/api/users/weekend-vault").header(HttpHeaders.AUTHORIZATION, bearer(child1TokenSun)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressPercent").value(0))
                .andExpect(jsonPath("$.data.locked").value(true));
        mockMvc.perform(get(grantUrl))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "WEEKEND_VAULT"));
    }

    // test phụ huynh mở khóa thủ công bằng mã PIN (từ hồ sơ chính hoặc ngay trên máy ở kid mode) và khóa lại
    @Test
    void allowsParentPinManualUnlockAndRestoreLockWhileBlockingKidModeAbuse() throws Exception {
        String mainToken = registerUser("vaultdad", "vaultdad@example.com", "secret123", "Bố Sơn");
        enableParentMode(mainToken, "7788");
        long childId = createChildProfile(mainToken, "7788", "Bé Sóc", "owl", 60);
        String childToken = switchToChild(mainToken, childId);

        // kid mode không xem được tổng quan phụ huynh hay tự khóa lại
        mockMvc.perform(get("/api/users/profiles/weekend-vault").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users/profiles/" + childId + "/weekend-vault/lock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "7788"))))
                .andExpect(status().isForbidden());

        // sai PIN bị 400
        mockMvc.perform(post("/api/users/profiles/" + childId + "/weekend-vault/unlock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "0000"))))
                .andExpect(status().isBadRequest());

        // đúng PIN từ kid mode mở khóa két sắt ngay trong ngày thường
        mockMvc.perform(post("/api/users/profiles/" + childId + "/weekend-vault/unlock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "7788"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status.manualUnlocked").value(true))
                .andExpect(jsonPath("$.data.status.locked").value(false));

        mockMvc.perform(post("/api/media/grant/asset_solo_1").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk());

        // phụ huynh từ hồ sơ chính xem tổng quan và khóa lại
        mockMvc.perform(get("/api/users/profiles/weekend-vault").header(HttpHeaders.AUTHORIZATION, bearer(mainToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status.manualUnlocked").value(true));

        mockMvc.perform(post("/api/users/profiles/" + childId + "/weekend-vault/lock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "7788"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status.manualUnlocked").value(false))
                .andExpect(jsonPath("$.data.status.locked").value(true));

        mockMvc.perform(post("/api/media/grant/asset_solo_1").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "WEEKEND_VAULT"));
    }

    private void addLedger(long profileId, LocalDate day, ScreenTimeLedgerKind kind, int seconds) {
        ledgerRepository.saveAndFlush(new ScreenTimeLedgerEntry(
                profileId,
                day,
                kind,
                seconds,
                LocalDateTime.of(day, java.time.LocalTime.NOON)
        ));
    }

    private void seedStandaloneFilmAndSeriesEpisode() {
        Movie soloSeriesMovie = new Movie("ser_solo", "Phim Lẻ Cuối Tuần", 2024, 8.7, "90 phút", "[\"Family\"]", "Phim lẻ", null, null, null);
        Movie soloFilm = new Movie("film_solo_1", "Chú Gấu Phiêu Lưu", 2024, 8.7, "90 phút", "[\"Family\"]", "Phim lẻ cuối tuần", null, null, null);
        Movie seriesMovie = new Movie("ser_reg", "Mèo Con", 2024, 8.5, "10 phút", "[\"Family\"]", "Phim bộ", null, null, null);
        Movie epMovie = new Movie("ep_reg_1", "Tập 1", 2024, 8.5, "10 phút", "[\"Family\"]", "Tập 1", null, null, null);
        movieRepository.saveAllAndFlush(List.of(soloSeriesMovie, soloFilm, seriesMovie, epMovie));

        seriesRepository.saveAllAndFlush(List.of(
                new CatalogSeries(
                        "ser_solo", "Phim Lẻ Cuối Tuần", null, null, null, null, "[\"family\"]", null, null, null, null,
                        2024, null, null, null, null, null, null, null, null, null, READY_JSON, "{\"id\":\"ser_solo\"}", 1
                ),
                new CatalogSeries(
                        "ser_reg", "Mèo Con", null, null, null, null, "[\"family\"]", null, null, null, null,
                        2024, null, null, null, null, null, null, null, null, null, READY_JSON, "{\"id\":\"ser_reg\"}", 1
                )
        ));
        catalogMediaAssetRepository.saveAndFlush(new CatalogMediaAsset(
                "asset_solo_1", "video", "feature", "Bản chuẩn", null, null,
                "local", "local", "solo-film.mp4", null, null,
                8L, null, 5_400_000L, null,
                "[{\"episode_id\":\"film_solo_1\",\"scope\":\"full_episode\"}]",
                null, "public", true, READY_JSON, "{\"id\":\"asset_solo_1\"}", 1
        ));
        mediaAssetRepository.saveAndFlush(new MediaAsset(
                "asset_solo_1", "film_solo_1", "feature", "local", "local",
                "solo-film.mp4", "v1", "video/mp4", 8L, 5400.0, true, true
        ));
        episodeRepository.saveAndFlush(new CatalogEpisode(
                "film_solo_1", "ser_solo", null, 1, "Chú Gấu Phiêu Lưu", null, null, null, "movie", null,
                5_400_000L, null, null, null, null, null, null, null, null, null, null, null, null,
                READY_JSON, "{\"id\":\"film_solo_1\"}", 1
        ));
        writes.executeWithoutResult(status -> entityManager.persist(new CatalogEpisodeAsset("film_solo_1", "asset_solo_1")));

        catalogMediaAssetRepository.saveAndFlush(new CatalogMediaAsset(
                "asset_reg_ep1", "video", "feature", "Bản chuẩn", null, null,
                "local", "local", "series-ep1.mp4", null, null,
                8L, null, 600_000L, null,
                "[{\"episode_id\":\"ep_reg_1\",\"scope\":\"full_episode\"}]",
                null, "authenticated", true, READY_JSON, "{\"id\":\"asset_reg_ep1\"}", 1
        ));
        mediaAssetRepository.saveAndFlush(new MediaAsset(
                "asset_reg_ep1", "ep_reg_1", "feature", "local", "local",
                "series-ep1.mp4", "v1", "video/mp4", 8L, 600.0, false, true
        ));
        episodeRepository.saveAndFlush(new CatalogEpisode(
                "ep_reg_1", "ser_reg", null, 1, "Tập 1", null, null, null, "regular", null,
                600_000L, null, null, null, null, null, null, null, null, null, null, null, null,
                READY_JSON, "{\"id\":\"ep_reg_1\"}", 1
        ));
        writes.executeWithoutResult(status -> entityManager.persist(new CatalogEpisodeAsset("ep_reg_1", "asset_reg_ep1")));
        catalogReadModelCache.invalidate();
    }

    private String registerUser(String username, String email, String password, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "username", username,
                                "email", email,
                                "password", password,
                                "fullName", fullName,
                                "acceptedTerms", true
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private String loginUser(String usernameOrEmail, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("usernameOrEmail", usernameOrEmail, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private void enableParentMode(String token, String pin) throws Exception {
        mockMvc.perform(post("/api/users/parent-mode/enable")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", pin))))
                .andExpect(status().isOk());
    }

    private long createChildProfile(String token, String pin, String name, String avatarKey, int dailyMinutes) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/profiles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", pin, "name", name, "avatarKey", avatarKey, "dailyMinutes", dailyMinutes))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private String switchToChild(String token, long profileId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/profiles/" + profileId + "/switch")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private String json(Object payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    static final class MutableClock extends Clock {

        private Instant current;

        MutableClock(Instant initial) {
            this.current = initial;
        }

        void set(Instant instant) {
            this.current = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
