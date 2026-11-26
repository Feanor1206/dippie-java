package com.dippie.controller.vip;

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
import com.dippie.model.quiz.QuizChallengeContext;
import com.dippie.model.quiz.QuizGrade;
import com.dippie.model.quiz.QuizOptionKey;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.profile.ScreenTimeLedgerRepository;
import com.dippie.repository.progress.WatchProgressRepository;
import com.dippie.repository.quiz.ChildQuizSetRepository;
import com.dippie.repository.quiz.QuizAttemptRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.repository.vip.ChildVipEpisodeUnlockRepository;
import com.dippie.repository.vip.ChildVipSeriesRepository;
import com.dippie.security.LoginRateLimiter;
import com.dippie.service.catalog.CatalogReadModelCache;
import com.dippie.service.profile.ParentPinRateLimiter;
import com.dippie.service.quiz.QuizRateLimiter;
import com.dippie.service.quiz.QuizSelectionService;
import com.dippie.service.quiz.QuizTokenCodec;
import com.dippie.service.vip.VipTitleService;

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

// test phim VIP theo độ khó: gắn cờ theo từng bé, khóa 423 ở grant/stream/progress, giải toán mức +1 (trần 4) mở đúng 1 tập trong ngày
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(VipTitleControllerTest.ClockOverrideConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class VipTitleControllerTest {

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
        MutableClock vipMutableClock() {
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
    private final ChildVipSeriesRepository vipSeriesRepository;
    private final ChildVipEpisodeUnlockRepository vipUnlockRepository;
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
    private final QuizRateLimiter quizRateLimiter;
    private final QuizTokenCodec quizTokenCodec;
    private final QuizSelectionService quizSelectionService;
    private final ChildQuizSetRepository childQuizSetRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    VipTitleControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            MutableClock clock,
            UserRepository userRepository,
            ChildProfileRepository childProfileRepository,
            ScreenTimeLedgerRepository ledgerRepository,
            WatchProgressRepository watchProgressRepository,
            ChildVipSeriesRepository vipSeriesRepository,
            ChildVipEpisodeUnlockRepository vipUnlockRepository,
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
            QuizRateLimiter quizRateLimiter,
            QuizTokenCodec quizTokenCodec,
            QuizSelectionService quizSelectionService,
            ChildQuizSetRepository childQuizSetRepository,
            QuizAttemptRepository quizAttemptRepository
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.userRepository = userRepository;
        this.childProfileRepository = childProfileRepository;
        this.ledgerRepository = ledgerRepository;
        this.watchProgressRepository = watchProgressRepository;
        this.vipSeriesRepository = vipSeriesRepository;
        this.vipUnlockRepository = vipUnlockRepository;
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
        this.quizRateLimiter = quizRateLimiter;
        this.quizTokenCodec = quizTokenCodec;
        this.quizSelectionService = quizSelectionService;
        this.childQuizSetRepository = childQuizSetRepository;
        this.quizAttemptRepository = quizAttemptRepository;
    }

    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.parse("2026-11-26T10:00:00Z"));
        loginRateLimiter.reset();
        parentPinRateLimiter.reset();
        quizRateLimiter.reset();
        quizTokenCodec.resetUsedTokens();
        quizSelectionService.resetRecentIssued();
        quizAttemptRepository.deleteAll();
        childQuizSetRepository.deleteAll();
        vipUnlockRepository.deleteAll();
        vipSeriesRepository.deleteAll();
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
        Files.write(mediaRoot.resolve("vip-ep1.mp4"), new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        Files.write(mediaRoot.resolve("vip-ep2.mp4"), new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        Files.write(mediaRoot.resolve("norm-ep1.mp4"), new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        seedCatalog();
    }

    // test gắn phim VIP riêng cho bé 1, chặn 423 VIP_EPISODE, giải đúng toán mức +1 mở đúng 1 tập trong ngày và tự khóa lại sau 0h VN
    @Test
    void enforcesPerChildVipLockUnlocksSingleEpisodeForTodayAndResetsAtMidnight() throws Exception {
        String mainToken = registerUser("vipmom", "vipmom@example.com", "secret123", "Mẹ Trang");
        enableParentMode(mainToken, "3690");
        long child1Id = createChildProfile(mainToken, "3690", "Bé Kẹo", "star");
        long child2Id = createChildProfile(mainToken, "3690", "Bé Su", "panda");
        String child1Token = switchToChild(mainToken, child1Id);
        String child2Token = switchToChild(mainToken, child2Id);

        // đặt lớp bé 1 là LOP_2 -> câu hỏi VIP phải lên LOP_3
        mockMvc.perform(post("/api/users/quiz/profiles/" + child1Id + "/grade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "3690", "grade", "LOP_2"))))
                .andExpect(status().isOk());

        // kid mode không được chỉnh danh sách VIP
        mockMvc.perform(put("/api/users/profiles/" + child1Id + "/vip-series")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "3690", "seriesIds", List.of("ser_vip")))))
                .andExpect(status().isForbidden());

        // phụ huynh đánh dấu ser_vip là VIP cho riêng bé 1
        mockMvc.perform(put("/api/users/profiles/" + child1Id + "/vip-series")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "3690", "seriesIds", List.of("ser_vip")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizGrade").value("LOP_2"))
                .andExpect(jsonPath("$.data.vipQuizGrade").value("LOP_3"))
                .andExpect(jsonPath("$.data.seriesIds[0]").value("ser_vip"));

        // bé 1 bị chặn 423 VIP_EPISODE ở grant, stream và watch-progress với ep_vip_1
        mockMvc.perform(post("/api/media/grant/asset_vip_1").header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "VIP_EPISODE"));
        mockMvc.perform(get("/api/media/stream/asset_vip_1").header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "VIP_EPISODE"));
        mockMvc.perform(put("/api/users/watch-progress/ep_vip_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assetId", "asset_vip_1", "positionMs", 10_000, "expectedVersion", -1))))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "VIP_EPISODE"));

        // phim thường của bé 1, bé 2 và hồ sơ chính vẫn phát 200 OK
        mockMvc.perform(post("/api/media/grant/asset_norm_1").header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/media/grant/asset_vip_1").header(HttpHeaders.AUTHORIZATION, bearer(child2Token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/media/grant/asset_vip_1").header(HttpHeaders.AUTHORIZATION, bearer(mainToken)))
                .andExpect(status().isOk());

        // lấy câu hỏi VIP cho ep_vip_1: phải là LOP_3, đủ 4 đáp án A-D và không lộ đáp án đúng
        MvcResult challengeRes = mockMvc.perform(get("/api/users/vip-series/episodes/ep_vip_1/challenge")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grade").value("LOP_3"))
                .andExpect(jsonPath("$.data.options.length()").value(4))
                .andExpect(jsonPath("$.data.episodeId").value("ep_vip_1"))
                .andExpect(jsonPath("$.data.answer").doesNotExist())
                .andExpect(jsonPath("$.data.correctOption").doesNotExist())
                .andReturn();
        JsonNode challenge = objectMapper.readTree(challengeRes.getResponse().getContentAsString()).path("data");
        String vipToken = challenge.path("token").asText();
        QuizOptionKey correctOption = decodeCorrect(vipToken, QuizChallengeContext.VIP_UNLOCK);

        // không dùng chéo được: token VIP không nộp sang screen-time, không nộp cho ep_vip_2, không nộp bằng bé 2
        mockMvc.perform(post("/api/users/screen-time/challenge/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", vipToken, "option", correctOption.name()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/users/vip-series/episodes/ep_vip_2/unlock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", vipToken, "option", correctOption.name()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/users/vip-series/episodes/ep_vip_1/unlock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child2Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", vipToken, "option", correctOption.name()))))
                .andExpect(status().isBadRequest());

        // trả lời đúng -> mở khóa đúng ep_vip_1 trong hôm nay, ep_vip_2 vẫn bị khóa
        mockMvc.perform(post("/api/users/vip-series/episodes/ep_vip_1/unlock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", vipToken, "option", correctOption.name()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.unlocked").value(true));

        // replay cùng token bị chặn 400
        mockMvc.perform(post("/api/users/vip-series/episodes/ep_vip_1/unlock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", vipToken, "option", correctOption.name()))))
                .andExpect(status().isBadRequest());

        // ep_vip_1 phát được và lưu tiến độ được, còn ep_vip_2 vẫn khóa 423
        MvcResult grantRes = mockMvc.perform(post("/api/media/grant/asset_vip_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isOk())
                .andReturn();
        String streamUrl = objectMapper.readTree(grantRes.getResponse().getContentAsString()).path("streamUrl").asText();
        mockMvc.perform(get(streamUrl)).andExpect(status().isOk());
        mockMvc.perform(put("/api/users/watch-progress/ep_vip_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assetId", "asset_vip_1", "positionMs", 15_000, "expectedVersion", -1))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/media/grant/asset_vip_2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "VIP_EPISODE"));

        // qua nửa đêm Việt Nam (2026-11-27T00:00+07:00 = 2026-11-26T17:00:00Z): ep_vip_1 khóa lại
        clock.set(Instant.parse("2026-11-26T17:00:00Z"));
        mockMvc.perform(post("/api/media/grant/asset_vip_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(child1Token)))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "VIP_EPISODE"));
    }

    // test trần độ khó lớp 5 (khi bé đang ở lớp 5), từ chối token screen-time, hết hạn 2 phút và rate limit 10 lần sai
    @Test
    void capsVipDifficultyAtGradeFiveRejectsCrossContextAndExpiredTokensAndRateLimitsWrongAnswers() throws Exception {
        String mainToken = registerUser("vipdad", "vipdad@example.com", "secret123", "Bố Huy");
        enableParentMode(mainToken, "8080");
        long childId = createChildProfile(mainToken, "8080", "Bé Bờm", "owl");
        String childToken = switchToChild(mainToken, childId);

        mockMvc.perform(post("/api/users/quiz/profiles/" + childId + "/grade")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "8080", "grade", "LOP_5"))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/users/profiles/" + childId + "/vip-series")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "8080", "seriesIds", List.of("ser_vip")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.vipQuizGrade").value("LOP_5"));

        // token screen-time không mở khóa được tập VIP
        MvcResult stRes = mockMvc.perform(get("/api/users/screen-time/challenge")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andReturn();
        String stToken = objectMapper.readTree(stRes.getResponse().getContentAsString()).path("data").path("token").asText();
        mockMvc.perform(post("/api/users/vip-series/episodes/ep_vip_1/unlock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", stToken, "option", decodeCorrect(stToken, QuizChallengeContext.SCREEN_TIME).name()))))
                .andExpect(status().isBadRequest());

        // lấy đề lớp 5 rồi tua quá 2 phút -> hết hạn bị 400
        MvcResult expiredRes = mockMvc.perform(get("/api/users/vip-series/episodes/ep_vip_1/challenge")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grade").value("LOP_5"))
                .andReturn();
        String expiredToken = objectMapper.readTree(expiredRes.getResponse().getContentAsString()).path("data").path("token").asText();
        clock.advance(Duration.ofSeconds(121));
        mockMvc.perform(post("/api/users/vip-series/episodes/ep_vip_1/unlock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "token", expiredToken,
                                "option", decodeCorrect(expiredToken, QuizChallengeContext.VIP_UNLOCK).name()
                        ))))
                .andExpect(status().isBadRequest());

        // trả lời sai 10 lần -> lần 11 bị 429 kèm Retry-After
        for (int i = 0; i < 10; i++) {
            MvcResult freshRes = mockMvc.perform(get("/api/users/vip-series/episodes/ep_vip_1/challenge")
                            .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                    .andExpect(status().isOk())
                    .andReturn();
            String freshToken = objectMapper.readTree(freshRes.getResponse().getContentAsString()).path("data").path("token").asText();
            QuizOptionKey wrong = pickWrongOption(decodeCorrect(freshToken, QuizChallengeContext.VIP_UNLOCK));
            mockMvc.perform(post("/api/users/vip-series/episodes/ep_vip_1/unlock")
                            .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("token", freshToken, "option", wrong.name()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.correct").value(false));
        }
        MvcResult blockedRes = mockMvc.perform(get("/api/users/vip-series/episodes/ep_vip_1/challenge")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andReturn();
        String blockedToken = objectMapper.readTree(blockedRes.getResponse().getContentAsString()).path("data").path("token").asText();
        mockMvc.perform(post("/api/users/vip-series/episodes/ep_vip_1/unlock")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", blockedToken, "option", "A"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        assertThat(VipTitleService.requiredVipGrade(QuizGrade.MAU_GIAO)).isEqualTo(QuizGrade.LOP_1);
        assertThat(VipTitleService.requiredVipGrade(QuizGrade.LOP_3)).isEqualTo(QuizGrade.LOP_4);
        assertThat(VipTitleService.requiredVipGrade(QuizGrade.LOP_5)).isEqualTo(QuizGrade.LOP_5);
    }

    private QuizOptionKey decodeCorrect(String token, QuizChallengeContext context) {
        return quizTokenCodec.decode(token, context).orElseThrow().correctOption();
    }

    private static QuizOptionKey pickWrongOption(QuizOptionKey correct) {
        return correct == QuizOptionKey.A ? QuizOptionKey.B : QuizOptionKey.A;
    }

    private void seedCatalog() {
        movieRepository.saveAllAndFlush(List.of(
                new Movie("ser_vip", "Siêu Nhân Toán Học", 2024, 9.0, "15 phút", "[\"Family\"]", "Phim VIP", null, null, null),
                new Movie("ep_vip_1", "VIP Tập 1", 2024, 9.0, "15 phút", "[\"Family\"]", "Tập 1", null, null, null),
                new Movie("ep_vip_2", "VIP Tập 2", 2024, 9.0, "15 phút", "[\"Family\"]", "Tập 2", null, null, null),
                new Movie("ser_norm", "Thỏ Nâu", 2024, 8.2, "10 phút", "[\"Family\"]", "Phim thường", null, null, null),
                new Movie("ep_norm_1", "Thường Tập 1", 2024, 8.2, "10 phút", "[\"Family\"]", "Tập 1", null, null, null)
        ));
        seriesRepository.saveAllAndFlush(List.of(
                new CatalogSeries("ser_vip", "Siêu Nhân Toán Học", null, null, null, null, "[\"family\"]", null, null, null, null,
                        2024, null, null, null, null, null, null, null, null, null, READY_JSON, "{\"id\":\"ser_vip\"}", 1),
                new CatalogSeries("ser_norm", "Thỏ Nâu", null, null, null, null, "[\"family\"]", null, null, null, null,
                        2024, null, null, null, null, null, null, null, null, null, READY_JSON, "{\"id\":\"ser_norm\"}", 1)
        ));
        addEpisodeWithFile("ser_vip", "ep_vip_1", 1, "VIP Tập 1", "asset_vip_1", "vip-ep1.mp4");
        addEpisodeWithFile("ser_vip", "ep_vip_2", 2, "VIP Tập 2", "asset_vip_2", "vip-ep2.mp4");
        addEpisodeWithFile("ser_norm", "ep_norm_1", 1, "Thường Tập 1", "asset_norm_1", "norm-ep1.mp4");
        catalogReadModelCache.invalidate();
    }

    private void addEpisodeWithFile(String seriesId, String episodeId, int number, String title, String assetId, String file) {
        catalogMediaAssetRepository.saveAndFlush(new CatalogMediaAsset(
                assetId, "video", "feature", "Bản chuẩn", null, null,
                "local", "local", file, null, null,
                8L, null, 300_000L, null,
                "[{\"episode_id\":\"" + episodeId + "\",\"scope\":\"full_episode\"}]",
                null, "authenticated", true, READY_JSON, "{\"id\":\"" + assetId + "\"}", 1
        ));
        mediaAssetRepository.saveAndFlush(new MediaAsset(
                assetId, episodeId, "feature", "local", "local",
                file, "v1", "video/mp4", 8L, 300.0, false, true
        ));
        episodeRepository.saveAndFlush(new CatalogEpisode(
                episodeId, seriesId, null, number, title, null, null, null, "regular", null,
                300_000L, null, null, null, null, null, null, null, null, null, null, null, null,
                READY_JSON, "{\"id\":\"" + episodeId + "\"}", 1
        ));
        writes.executeWithoutResult(status -> entityManager.persist(new CatalogEpisodeAsset(episodeId, assetId)));
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
