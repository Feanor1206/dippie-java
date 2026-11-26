package com.dippie.controller.profile;

import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogEpisodeAsset;
import com.dippie.model.catalog.CatalogMediaAsset;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.media.MediaAsset;
import com.dippie.model.movie.Movie;
import com.dippie.repository.catalog.CatalogEpisodeRepository;
import com.dippie.repository.catalog.CatalogMediaAssetRepository;
import com.dippie.repository.catalog.CatalogSeriesRepository;
import com.dippie.repository.media.MediaAssetRepository;
import com.dippie.repository.movie.MovieRepository;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.profile.ScreenTimeLedgerRepository;
import com.dippie.repository.progress.WatchProgressRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.LoginRateLimiter;
import com.dippie.service.catalog.CatalogReadModelCache;
import com.dippie.service.profile.MathChallengeRateLimiter;
import com.dippie.service.profile.MathChallengeService;
import com.dippie.service.profile.ParentPinRateLimiter;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// test trọn luồng giới hạn giờ xem cho hồ sơ trẻ em: sổ cái qua 0h VN, heartbeat, khóa 423, giải toán và phụ huynh cho thêm phút
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ScreenTimeControllerTest.ClockOverrideConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ScreenTimeControllerTest {

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
        MutableClock testMutableClock() {
            return new MutableClock(Instant.parse("2026-11-26T10:00:00Z"));
        }
    }

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final MutableClock clock;
    private final UserRepository userRepository;
    private final ChildProfileRepository childProfileRepository;
    private final ScreenTimeLedgerRepository ledgerRepository;
    private final WatchProgressRepository watchProgressRepository;
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
    private final MathChallengeRateLimiter mathChallengeRateLimiter;
    private final MathChallengeService mathChallengeService;

    ScreenTimeControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            MutableClock clock,
            UserRepository userRepository,
            ChildProfileRepository childProfileRepository,
            ScreenTimeLedgerRepository ledgerRepository,
            WatchProgressRepository watchProgressRepository,
            MovieRepository movieRepository,
            MediaAssetRepository mediaAssetRepository,
            CatalogMediaAssetRepository catalogMediaAssetRepository,
            CatalogSeriesRepository seriesRepository,
            CatalogEpisodeRepository episodeRepository,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager,
            CatalogReadModelCache catalogReadModelCache,
            LoginRateLimiter loginRateLimiter,
            ParentPinRateLimiter parentPinRateLimiter,
            MathChallengeRateLimiter mathChallengeRateLimiter,
            MathChallengeService mathChallengeService
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.userRepository = userRepository;
        this.childProfileRepository = childProfileRepository;
        this.ledgerRepository = ledgerRepository;
        this.watchProgressRepository = watchProgressRepository;
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
        this.mathChallengeRateLimiter = mathChallengeRateLimiter;
        this.mathChallengeService = mathChallengeService;
    }

    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.parse("2026-11-26T10:00:00Z"));
        loginRateLimiter.reset();
        parentPinRateLimiter.reset();
        mathChallengeRateLimiter.reset();
        mathChallengeService.resetUsedTokens();
        ledgerRepository.deleteAll();
        watchProgressRepository.deleteAll();
        childProfileRepository.deleteAll();
        writes.executeWithoutResult(status -> entityManager.createQuery("delete from CatalogEpisodeAsset").executeUpdate());
        episodeRepository.deleteAll();
        seriesRepository.deleteAll();
        catalogMediaAssetRepository.deleteAll();
        mediaAssetRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.deleteAll();
        catalogReadModelCache.invalidate();
        Files.write(mediaRoot.resolve("kid-ep1.mp4"), new byte[]{0, 1, 2, 3, 4, 5, 6, 7});
    }

    // test heartbeat chặn trần 20s, bỏ qua nhịp sát nhau dưới 10s, khóa 423 ở grant/stream/progress và tự reset lúc 0h Asia/Ho_Chi_Minh
    @Test
    void enforcesHeartbeatCapsPlaybackLockAndMidnightResetInVietnamTimezone() throws Exception {
        seedPlayableCatalogWithFile();
        // đặt giờ ngay trước nửa đêm Việt Nam: 16:58:00Z = 23:58:00+07:00 ngày 2026-11-26
        clock.set(Instant.parse("2026-11-26T16:58:00Z"));

        String mainToken = registerUser("parent1", "parent1@example.com", "secret123", "Ba Mẹ");
        enableParentMode(mainToken, "1234");
        long childId = createChildProfile(mainToken, "1234", "Bé Bông", "bunny");

        // chỉnh hạn mức 1 phút/ngày để test hết giờ gọn gàng
        mockMvc.perform(put("/api/users/profiles/" + childId + "/screen-time")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", "1234",
                                "dailyMinutes", 1,
                                "mathLevel", 1,
                                "rewardMinutes", 5,
                                "maxBonusMinutes", 30
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status.dailyMinutes").value(1))
                .andExpect(jsonPath("$.data.status.remainingSeconds").value(60));

        String childToken = switchToChild(mainToken, childId);

        // khi còn giờ: xin vé phát, stream và lưu tiến độ đều 200 OK
        MvcResult grantBeforeLock = mockMvc.perform(post("/api/media/grant/asset_kid_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andReturn();
        String activeChildGrant = objectMapper.readTree(grantBeforeLock.getResponse().getContentAsString())
                .path("grant").asText();

        mockMvc.perform(get("/api/media/stream/asset_kid_1").param("grant", activeChildGrant))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/users/watch-progress/ep_screen_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assetId", "asset_kid_1", "positionMs", 15_000, "expectedVersion", -1))))
                .andExpect(status().isOk());

        // nhịp 1 gửi lố 90 giây -> server kẹp trần tối đa 20 giây (còn 40s)
        mockMvc.perform(post("/api/users/screen-time/heartbeat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("seconds", 90))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.creditedSeconds").value(20))
                .andExpect(jsonPath("$.data.ignored").value(false))
                .andExpect(jsonPath("$.data.remainingSeconds").value(40))
                .andExpect(jsonPath("$.data.locked").value(false));

        // nhịp 2 gửi sau 5 giây (< 10 giây) -> bị bỏ qua, không trừ thêm
        clock.advance(Duration.ofSeconds(5));
        mockMvc.perform(post("/api/users/screen-time/heartbeat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("seconds", 15))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.creditedSeconds").value(0))
                .andExpect(jsonPath("$.data.ignored").value(true))
                .andExpect(jsonPath("$.data.remainingSeconds").value(40))
                .andExpect(jsonPath("$.data.locked").value(false));

        // qua đủ 10 giây kể từ nhịp 1 -> trừ tiếp 20 giây (còn 20s)
        clock.advance(Duration.ofSeconds(6));
        mockMvc.perform(post("/api/users/screen-time/heartbeat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("seconds", 20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.creditedSeconds").value(20))
                .andExpect(jsonPath("$.data.remainingSeconds").value(20))
                .andExpect(jsonPath("$.data.locked").value(false));

        // 15 giây sau trừ tiếp 15s (còn 5s), rồi 15 giây sau gửi 15s chỉ trừ nốt 5s chạm 0 -> locked = true
        clock.advance(Duration.ofSeconds(15));
        mockMvc.perform(post("/api/users/screen-time/heartbeat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("seconds", 15))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.creditedSeconds").value(15))
                .andExpect(jsonPath("$.data.remainingSeconds").value(5))
                .andExpect(jsonPath("$.data.locked").value(false));

        clock.advance(Duration.ofSeconds(15));
        mockMvc.perform(post("/api/users/screen-time/heartbeat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("seconds", 15))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.creditedSeconds").value(5))
                .andExpect(jsonPath("$.data.remainingSeconds").value(0))
                .andExpect(jsonPath("$.data.locked").value(true));

        // lúc này vẫn đang ở 2026-11-26 giờ Việt Nam: cả grant, stream bằng vé cũ lẫn watch-progress đều bị chặn 423 Locked
        mockMvc.perform(post("/api/media/grant/asset_kid_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Hết giờ xem hôm nay rồi."));

        mockMvc.perform(get("/api/media/stream/asset_kid_1").param("grant", activeChildGrant))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.message").value("Hết giờ xem hôm nay rồi."));

        mockMvc.perform(put("/api/users/watch-progress/ep_screen_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assetId", "asset_kid_1", "positionMs", 30_000, "expectedVersion", 0))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.message").value("Hết giờ xem hôm nay rồi."));

        // tài nguyên ẩn vẫn trả 404 chứ không rò 423
        mockMvc.perform(post("/api/media/grant/asset_missing")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isNotFound());

        // bước qua 0h00 giờ Việt Nam (17:00:05Z = 00:00:05+07:00 ngày 2026-11-27) -> hạn mức tự làm mới, xem lại được ngay
        clock.set(Instant.parse("2026-11-26T17:00:05Z"));
        mockMvc.perform(get("/api/users/screen-time").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.day").value("2026-11-27"))
                .andExpect(jsonPath("$.data.watchedSeconds").value(0))
                .andExpect(jsonPath("$.data.remainingSeconds").value(60))
                .andExpect(jsonPath("$.data.locked").value(false));

        mockMvc.perform(post("/api/media/grant/asset_kid_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk());
    }

    // test giải toán cộng phút, chống dùng lại token, hết hạn 2 phút, khóa hồ sơ khác, trần thưởng ngày và giới hạn 10 lần sai
    @Test
    void enforcesMathChallengeSingleUseExpiryProfileBindingBonusCapAndWrongAnswerLimit() throws Exception {
        seedPlayableCatalogWithFile();
        String mainToken = registerUser("mathmom", "mathmom@example.com", "secret123", "Mẹ Na");
        enableParentMode(mainToken, "4321");
        long child1Id = createChildProfile(mainToken, "4321", "Bé Na", "cat");
        long child2Id = createChildProfile(mainToken, "4321", "Bé Bi", "dino");

        // cấu hình child1: 0 phút mặc định (khóa sẵn), mức 4 (nhân/chia), thưởng 10 phút/câu, trần 15 phút/ngày
        mockMvc.perform(put("/api/users/profiles/" + child1Id + "/screen-time")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", "4321",
                                "dailyMinutes", 0,
                                "mathLevel", 4,
                                "rewardMinutes", 10,
                                "maxBonusMinutes", 15
                        ))))
                .andExpect(status().isOk());

        String child1Token = switchToChild(mainToken, child1Id);
        String child2Token = switchToChild(mainToken, child2Id);

        // ban đầu child1 bị khóa ở 0 giây
        mockMvc.perform(post("/api/media/grant/asset_kid_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isLocked());

        // lấy câu hỏi 1, response tuyệt đối không chứa trường answer
        MvcResult challengeRes1 = mockMvc.perform(get("/api/users/screen-time/challenge")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.question").isNotEmpty())
                .andExpect(jsonPath("$.data.mathLevel").value(4))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(120))
                .andExpect(jsonPath("$.data.answer").doesNotExist())
                .andReturn();
        JsonNode ch1 = objectMapper.readTree(challengeRes1.getResponse().getContentAsString()).path("data");
        String token1 = ch1.path("token").asText();
        int answer1 = solveQuestion(ch1.path("question").asText());

        // bé 2 dùng token của bé 1 bị từ chối 400
        mockMvc.perform(post("/api/users/screen-time/challenge/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token1, "answer", answer1))))
                .andExpect(status().isBadRequest());

        // nộp đáp án sai -> không cộng phút
        mockMvc.perform(post("/api/users/screen-time/challenge/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token1, "answer", answer1 + 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.earnedSeconds").value(0))
                .andExpect(jsonPath("$.data.status.remainingSeconds").value(0));

        // nộp đúng -> được +10 phút (600s) và mở khóa xem phim ngay
        mockMvc.perform(post("/api/users/screen-time/challenge/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token1, "answer", answer1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.earnedSeconds").value(600))
                .andExpect(jsonPath("$.data.earnedMinutes").value(10))
                .andExpect(jsonPath("$.data.bonusCapReached").value(false))
                .andExpect(jsonPath("$.data.status.remainingSeconds").value(600))
                .andExpect(jsonPath("$.data.status.locked").value(false));

        mockMvc.perform(post("/api/media/grant/asset_kid_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isOk());

        // dùng lại token1 lần nữa -> bị chặn 400 không cho ăn thưởng 2 lần
        mockMvc.perform(post("/api/users/screen-time/challenge/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token1, "answer", answer1))))
                .andExpect(status().isBadRequest());
        assertThat(mathChallengeService.trackedUsedTokenCount()).isEqualTo(1);

        // lấy trước 2 token (token2 và token3) rồi test hết hạn 2 phút trên token3 trước
        JsonNode chExpired = fetchChallenge(child1Token);
        clock.advance(Duration.ofSeconds(121));
        mockMvc.perform(post("/api/users/screen-time/challenge/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "token", chExpired.path("token").asText(),
                                "answer", solveQuestion(chExpired.path("question").asText())
                        ))))
                .andExpect(status().isBadRequest());
        // token1 cũng đã quá 2 phút nên bị dọn khỏi bộ nhớ lưu token đã dùng
        assertThat(mathChallengeService.trackedUsedTokenCount()).isZero();

        // lấy trước 2 token cùng lúc để kiểm tra kẹp trần thưởng 15 phút/ngày (còn lại đúng 5 phút)
        JsonNode ch2 = fetchChallenge(child1Token);
        JsonNode ch3 = fetchChallenge(child1Token);
        mockMvc.perform(post("/api/users/screen-time/challenge/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "token", ch2.path("token").asText(),
                                "answer", solveQuestion(ch2.path("question").asText())
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.earnedSeconds").value(300))
                .andExpect(jsonPath("$.data.earnedMinutes").value(5))
                .andExpect(jsonPath("$.data.bonusCapReached").value(true))
                .andExpect(jsonPath("$.data.status.rewardSeconds").value(900));

        // đã đạt trần thưởng ngày -> lấy câu mới bị 409, nộp token lấy sẵn trước đó cũng chỉ cộng 0 giây
        mockMvc.perform(get("/api/users/screen-time/challenge")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/users/screen-time/challenge/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "token", ch3.path("token").asText(),
                                "answer", solveQuestion(ch3.path("question").asText())
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.earnedSeconds").value(0))
                .andExpect(jsonPath("$.data.status.rewardSeconds").value(900));

        // test rate limit 10 lần trả lời sai trong 10 phút trên child2
        JsonNode chChild2 = fetchChallenge(child2Token);
        int wrongAnswer = solveQuestion(chChild2.path("question").asText()) + 99;
        for (int attempt = 0; attempt < 10; attempt++) {
            mockMvc.perform(post("/api/users/screen-time/challenge/answer")
                            .header(HttpHeaders.AUTHORIZATION, bearer(child2Token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("token", chChild2.path("token").asText(), "answer", wrongAnswer))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.correct").value(false));
        }
        mockMvc.perform(post("/api/users/screen-time/challenge/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", chChild2.path("token").asText(), "answer", wrongAnswer))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    // test phụ huynh sửa cài đặt, cho thêm 15 phút cần đúng PIN, chặn kid mode 403 và hồ sơ chính không bao giờ bị giới hạn
    @Test
    void supportsParentGrantWithPinBlocksKidModeManagementAndNeverLimitsMainProfile() throws Exception {
        seedPlayableCatalogWithFile();
        String mainToken = registerUser("dad", "dad@example.com", "secret123", "Bố Minh");
        String plainToken = registerUser("solo", "solo@example.com", "secret123", "Solo Viewer");
        enableParentMode(mainToken, "2468");
        long childId = createChildProfile(mainToken, "2468", "Bé Sóc", "owl");

        // validate khoảng giá trị cài đặt và chặn sai PIN
        mockMvc.perform(put("/api/users/profiles/" + childId + "/screen-time")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", "9999",
                                "dailyMinutes", 30,
                                "mathLevel", 2,
                                "rewardMinutes", 5,
                                "maxBonusMinutes", 30
                        ))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/users/profiles/" + childId + "/screen-time")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", "2468",
                                "dailyMinutes", 601,
                                "mathLevel", 2,
                                "rewardMinutes", 5,
                                "maxBonusMinutes", 30
                        ))))
                .andExpect(status().isBadRequest());

        // đặt 0 phút để khóa bé, sau đó kiểm tra kid mode không tự gọi API quản lý của phụ huynh được (403)
        mockMvc.perform(put("/api/users/profiles/" + childId + "/screen-time")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", "2468",
                                "dailyMinutes", 0,
                                "mathLevel", 2,
                                "rewardMinutes", 5,
                                "maxBonusMinutes", 0
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status.locked").value(true))
                .andExpect(jsonPath("$.data.status.bonusCapReached").value(true));

        String childToken = switchToChild(mainToken, childId);

        mockMvc.perform(get("/api/users/profiles/screen-time")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/users/profiles/" + childId + "/screen-time")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", "2468",
                                "dailyMinutes", 600,
                                "mathLevel", 1,
                                "rewardMinutes", 30,
                                "maxBonusMinutes", 120
                        ))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users/profiles/" + childId + "/screen-time/grant")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "2468", "minutes", 15))))
                .andExpect(status().isForbidden());

        // hồ sơ chính (dù bé đang bị khóa ở 0 giây) và tài khoản không bật parent mode không bao giờ bị giới hạn
        for (String adultToken : List.of(mainToken, plainToken)) {
            mockMvc.perform(get("/api/users/screen-time").header(HttpHeaders.AUTHORIZATION, bearer(adultToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.limited").value(false))
                    .andExpect(jsonPath("$.data.locked").value(false))
                    .andExpect(jsonPath("$.data.remainingSeconds").isEmpty());

            mockMvc.perform(post("/api/users/screen-time/heartbeat")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adultToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("seconds", 15))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.limited").value(false))
                    .andExpect(jsonPath("$.data.creditedSeconds").value(0))
                    .andExpect(jsonPath("$.data.locked").value(false));

            mockMvc.perform(post("/api/media/grant/asset_kid_1")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adultToken)))
                    .andExpect(status().isOk());

            mockMvc.perform(put("/api/users/watch-progress/ep_screen_1")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adultToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("assetId", "asset_kid_1", "positionMs", 10_000, "expectedVersion", -1))))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/users/screen-time/challenge")
                            .header(HttpHeaders.AUTHORIZATION, bearer(adultToken)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(ledgerRepository.count()).isZero();

        // phụ huynh tặng 15 phút: sai PIN bị 400, đúng PIN cộng 900 giây và mở khóa ngay cho bé
        mockMvc.perform(post("/api/users/profiles/" + childId + "/screen-time/grant")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "0000", "minutes", 15))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/users/profiles/" + childId + "/screen-time/grant")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "2468", "minutes", 15))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status.grantSeconds").value(900))
                .andExpect(jsonPath("$.data.status.remainingSeconds").value(900))
                .andExpect(jsonPath("$.data.status.locked").value(false));

        // tổng quan phụ huynh trả đủ thông số hôm nay của bé
        mockMvc.perform(get("/api/users/profiles/screen-time")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].profileId").value(childId))
                .andExpect(jsonPath("$.data[0].status.grantSeconds").value(900))
                .andExpect(jsonPath("$.data[0].status.remainingSeconds").value(900));

        // bé đã được mở khóa nên xin vé phát lại 200 OK
        mockMvc.perform(post("/api/media/grant/asset_kid_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk());
    }

    // test đổi phút xem mỗi ngày qua đúng API UI gọi, tạo hồ sơ kèm phút/không giới hạn và kiểm tra đồng bộ tức thì
    @Test
    void changesDailyMinutesThroughUiApiAndSupportsUnlimitedChildProfiles() throws Exception {
        seedPlayableCatalogWithFile();
        String mainToken = registerUser("mom1", "mom1@example.com", "secret123", "Mẹ Lan");
        enableParentMode(mainToken, "4321");

        // tạo bé 1 có sẵn hạn mức 90 phút và bé 2 chọn không giới hạn ngay lúc tạo
        MvcResult child1Res = mockMvc.perform(post("/api/users/profiles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "4321", "name", "Bé Na", "avatarKey", "bunny", "dailyMinutes", 90))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.dailyMinutes").value(90))
                .andExpect(jsonPath("$.data.unlimited").value(false))
                .andReturn();
        long child1Id = objectMapper.readTree(child1Res.getResponse().getContentAsString()).path("data").path("id").asLong();

        MvcResult child2Res = mockMvc.perform(post("/api/users/profiles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "4321", "name", "Bé Tin", "avatarKey", "dino", "unlimited", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.dailyMinutes").isEmpty())
                .andExpect(jsonPath("$.data.unlimited").value(true))
                .andReturn();
        long child2Id = objectMapper.readTree(child2Res.getResponse().getContentAsString()).path("data").path("id").asLong();

        String child1Token = switchToChild(mainToken, child1Id);
        String child2Token = switchToChild(mainToken, child2Id);

        // bé 1 đang có 90 phút = 5400 giây
        mockMvc.perform(get("/api/users/screen-time").header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limited").value(true))
                .andExpect(jsonPath("$.data.unlimited").value(false))
                .andExpect(jsonPath("$.data.dailyMinutes").value(90))
                .andExpect(jsonPath("$.data.remainingSeconds").value(5400));

        // phụ huynh đổi bé 1 sang 30 phút bằng đúng PUT /api/users/profiles/{id}/screen-time như UI gọi
        mockMvc.perform(put("/api/users/profiles/" + child1Id + "/screen-time")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", "4321",
                                "dailyMinutes", 30,
                                "unlimited", false,
                                "mathLevel", 2,
                                "rewardMinutes", 10,
                                "maxBonusMinutes", 40
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status.dailyMinutes").value(30))
                .andExpect(jsonPath("$.data.status.unlimited").value(false))
                .andExpect(jsonPath("$.data.status.remainingSeconds").value(1800));

        // tab bé 1 gọi trạng thái hoặc heartbeat tiếp theo thấy ngay 30 phút
        mockMvc.perform(post("/api/users/screen-time/heartbeat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("seconds", 15))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limited").value(true))
                .andExpect(jsonPath("$.data.creditedSeconds").value(15))
                .andExpect(jsonPath("$.data.remainingSeconds").value(1785))
                .andExpect(jsonPath("$.data.status.dailyMinutes").value(30));

        // phụ huynh bấm preset nhanh 120 phút (chỉ gửi pin + dailyMinutes, giữ nguyên cấu hình toán)
        mockMvc.perform(put("/api/users/profiles/" + child1Id + "/screen-time")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "4321", "dailyMinutes", 120, "unlimited", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status.dailyMinutes").value(120))
                .andExpect(jsonPath("$.data.status.mathLevel").value(2))
                .andExpect(jsonPath("$.data.status.rewardMinutes").value(10))
                .andExpect(jsonPath("$.data.status.remainingSeconds").value(7185));

        // chuyển bé 1 sang không giới hạn -> không khóa, không trừ heartbeat
        mockMvc.perform(put("/api/users/profiles/" + child1Id + "/screen-time")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "4321", "unlimited", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status.limited").value(false))
                .andExpect(jsonPath("$.data.status.unlimited").value(true))
                .andExpect(jsonPath("$.data.status.dailyMinutes").isEmpty())
                .andExpect(jsonPath("$.data.status.remainingSeconds").isEmpty())
                .andExpect(jsonPath("$.data.status.locked").value(false));

        clock.advance(Duration.ofSeconds(15));
        mockMvc.perform(post("/api/users/screen-time/heartbeat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("seconds", 15))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limited").value(false))
                .andExpect(jsonPath("$.data.creditedSeconds").value(0))
                .andExpect(jsonPath("$.data.locked").value(false))
                .andExpect(jsonPath("$.data.remainingSeconds").isEmpty())
                .andExpect(jsonPath("$.data.status.unlimited").value(true));

        // đổi ngược từ không giới hạn về 45 phút vẫn hoạt động bình thường
        mockMvc.perform(put("/api/users/profiles/" + child1Id + "/screen-time")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "4321", "dailyMinutes", 45, "unlimited", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status.limited").value(true))
                .andExpect(jsonPath("$.data.status.dailyMinutes").value(45))
                .andExpect(jsonPath("$.data.status.remainingSeconds").value(2685));

        // bé 2 tạo không giới hạn từ đầu: không bao giờ bị khóa ở status, heartbeat, grant hay watch-progress
        mockMvc.perform(get("/api/users/screen-time").header(HttpHeaders.AUTHORIZATION, bearer(child2Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limited").value(false))
                .andExpect(jsonPath("$.data.unlimited").value(true))
                .andExpect(jsonPath("$.data.locked").value(false))
                .andExpect(jsonPath("$.data.remainingSeconds").isEmpty());
        mockMvc.perform(post("/api/users/screen-time/heartbeat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("seconds", 15))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.limited").value(false))
                .andExpect(jsonPath("$.data.locked").value(false));
        mockMvc.perform(post("/api/media/grant/asset_kid_1").header(HttpHeaders.AUTHORIZATION, bearer(child2Token)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/users/watch-progress/ep_screen_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assetId", "asset_kid_1", "positionMs", 15_000, "expectedVersion", -1))))
                .andExpect(status().isOk());
    }

    private JsonNode fetchChallenge(String childToken) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/screen-time/challenge")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private static int solveQuestion(String question) {
        String[] parts = question.replace(" = ?", "").split(" ");
        int left = Integer.parseInt(parts[0]);
        int right = Integer.parseInt(parts[2]);
        return switch (parts[1]) {
            case "+" -> left + right;
            case "-" -> left - right;
            case "×" -> left * right;
            case "÷" -> left / right;
            default -> throw new IllegalArgumentException("Phép tính lạ: " + question);
        };
    }

    private void seedPlayableCatalogWithFile() {
        Movie seriesMovie = new Movie("ser_screen", "Hoạt Hình Gấu", 2024, 8.5, "10 phút", "[\"Family\"]", "Phim cho bé", null, null, null);
        Movie epMovie = new Movie("ep_screen_1", "Tập 1", 2024, 8.5, "5 phút", "[\"Family\"]", "Tập 1", null, null, null);
        movieRepository.saveAllAndFlush(List.of(seriesMovie, epMovie));

        seriesRepository.saveAndFlush(new CatalogSeries(
                "ser_screen", "Hoạt Hình Gấu", null, null, null, null, "[\"family\"]", null, null, null, null,
                2024, null, null, null, null, null, null, null, null, null, READY_JSON, "{\"id\":\"ser_screen\"}", 1
        ));

        CatalogMediaAsset catalogAsset = new CatalogMediaAsset(
                "asset_kid_1", "video", "feature", "Bản chuẩn", null, null,
                "local", "local", "kid-ep1.mp4", null, null,
                8L, null, 300_000L, null,
                "[{\"episode_id\":\"ep_screen_1\",\"scope\":\"full_episode\"}]",
                null, "authenticated", true, READY_JSON, "{\"id\":\"asset_kid_1\"}", 1
        );
        catalogMediaAssetRepository.saveAndFlush(catalogAsset);

        MediaAsset mediaAsset = new MediaAsset(
                "asset_kid_1", "ep_screen_1", "feature", "local", "local",
                "kid-ep1.mp4", "v1", "video/mp4", 8L, 300.0, false, true
        );
        mediaAssetRepository.saveAndFlush(mediaAsset);

        CatalogEpisode episode = new CatalogEpisode(
                "ep_screen_1", "ser_screen", null, 1, "Tập 1", null, null, null, "regular", null,
                300_000L, null, null, null, null, null, null, null, null, null, null, null, null,
                READY_JSON, "{\"id\":\"ep_screen_1\"}", 1
        );
        episodeRepository.saveAndFlush(episode);
        writes.executeWithoutResult(status -> entityManager.persist(new CatalogEpisodeAsset("ep_screen_1", "asset_kid_1")));
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

    private void enableParentMode(String token, String pin) throws Exception {
        mockMvc.perform(post("/api/users/parent-mode/enable")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", pin))))
                .andExpect(status().isOk());
    }

    private long createChildProfile(String token, String pin, String name, String avatarKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/profiles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", pin, "name", name, "avatarKey", avatarKey))))
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

    // đồng hồ giả cho bài test tua qua nửa đêm VN, khoảng cách nhịp tim và hết hạn token 2 phút
    static final class MutableClock extends Clock {

        private Instant current;

        MutableClock(Instant initial) {
            this.current = initial;
        }

        void set(Instant instant) {
            this.current = instant;
        }

        void advance(Duration duration) {
            this.current = this.current.plus(duration);
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
