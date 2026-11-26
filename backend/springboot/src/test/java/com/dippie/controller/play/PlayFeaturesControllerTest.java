package com.dippie.controller.play;

import com.dippie.model.catalog.CatalogCharacter;
import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogEpisodeAsset;
import com.dippie.model.catalog.CatalogMediaAsset;
import com.dippie.model.catalog.CatalogScene;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.media.MediaAsset;
import com.dippie.model.movie.Movie;
import com.dippie.model.profile.ScreenTimeLedgerEntry;
import com.dippie.model.profile.ScreenTimeLedgerKind;
import com.dippie.repository.catalog.CatalogCharacterRepository;
import com.dippie.repository.catalog.CatalogEpisodeRepository;
import com.dippie.repository.catalog.CatalogMediaAssetRepository;
import com.dippie.repository.catalog.CatalogSceneRepository;
import com.dippie.repository.catalog.CatalogSegmentRepository;
import com.dippie.repository.catalog.CatalogSeriesRepository;
import com.dippie.repository.media.MediaAssetRepository;
import com.dippie.repository.movie.MovieRepository;
import com.dippie.repository.play.ChildEpisodeRecapGateRepository;
import com.dippie.repository.play.ChildPlaySettingsRepository;
import com.dippie.repository.play.ChildPlayStateRepository;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.profile.ScreenTimeLedgerRepository;
import com.dippie.repository.progress.WatchProgressRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.LoginRateLimiter;
import com.dippie.service.catalog.CatalogReadModelCache;
import com.dippie.service.play.PlayTokenCodec;
import com.dippie.service.profile.MathChallengeRateLimiter;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// test trọn luồng 3 tính năng xem phim cho hồ sơ trẻ em: Dieppie nghỉ mắt, rương báu bay ngang, ôn tập qua tập và cài đặt phụ huynh
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PlayFeaturesControllerTest.ClockOverrideConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PlayFeaturesControllerTest {

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
        MutableClock playFeaturesTestClock() {
            return new MutableClock(Instant.parse("2026-11-26T10:00:00Z"));
        }
    }

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final MutableClock clock;
    private final UserRepository userRepository;
    private final ChildProfileRepository childProfileRepository;
    private final ChildPlaySettingsRepository playSettingsRepository;
    private final ChildPlayStateRepository playStateRepository;
    private final ChildEpisodeRecapGateRepository recapGateRepository;
    private final ScreenTimeLedgerRepository ledgerRepository;
    private final WatchProgressRepository watchProgressRepository;
    private final MovieRepository movieRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final CatalogMediaAssetRepository catalogMediaAssetRepository;
    private final CatalogSeriesRepository seriesRepository;
    private final CatalogEpisodeRepository episodeRepository;
    private final CatalogSceneRepository sceneRepository;
    private final CatalogSegmentRepository segmentRepository;
    private final CatalogCharacterRepository characterRepository;
    private final EntityManager entityManager;
    private final TransactionTemplate writes;
    private final CatalogReadModelCache catalogReadModelCache;
    private final LoginRateLimiter loginRateLimiter;
    private final ParentPinRateLimiter parentPinRateLimiter;
    private final MathChallengeRateLimiter mathChallengeRateLimiter;
    private final PlayTokenCodec playTokenCodec;

    PlayFeaturesControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            MutableClock clock,
            UserRepository userRepository,
            ChildProfileRepository childProfileRepository,
            ChildPlaySettingsRepository playSettingsRepository,
            ChildPlayStateRepository playStateRepository,
            ChildEpisodeRecapGateRepository recapGateRepository,
            ScreenTimeLedgerRepository ledgerRepository,
            WatchProgressRepository watchProgressRepository,
            MovieRepository movieRepository,
            MediaAssetRepository mediaAssetRepository,
            CatalogMediaAssetRepository catalogMediaAssetRepository,
            CatalogSeriesRepository seriesRepository,
            CatalogEpisodeRepository episodeRepository,
            CatalogSceneRepository sceneRepository,
            CatalogSegmentRepository segmentRepository,
            CatalogCharacterRepository characterRepository,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager,
            CatalogReadModelCache catalogReadModelCache,
            LoginRateLimiter loginRateLimiter,
            ParentPinRateLimiter parentPinRateLimiter,
            MathChallengeRateLimiter mathChallengeRateLimiter,
            PlayTokenCodec playTokenCodec
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.userRepository = userRepository;
        this.childProfileRepository = childProfileRepository;
        this.playSettingsRepository = playSettingsRepository;
        this.playStateRepository = playStateRepository;
        this.recapGateRepository = recapGateRepository;
        this.ledgerRepository = ledgerRepository;
        this.watchProgressRepository = watchProgressRepository;
        this.movieRepository = movieRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.catalogMediaAssetRepository = catalogMediaAssetRepository;
        this.seriesRepository = seriesRepository;
        this.episodeRepository = episodeRepository;
        this.sceneRepository = sceneRepository;
        this.segmentRepository = segmentRepository;
        this.characterRepository = characterRepository;
        this.entityManager = entityManager;
        this.writes = new TransactionTemplate(transactionManager);
        this.catalogReadModelCache = catalogReadModelCache;
        this.loginRateLimiter = loginRateLimiter;
        this.parentPinRateLimiter = parentPinRateLimiter;
        this.mathChallengeRateLimiter = mathChallengeRateLimiter;
        this.playTokenCodec = playTokenCodec;
    }

    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.parse("2026-11-26T10:00:00Z"));
        loginRateLimiter.reset();
        parentPinRateLimiter.reset();
        mathChallengeRateLimiter.reset();
        playTokenCodec.resetUsedTokens();
        recapGateRepository.deleteAll();
        playStateRepository.deleteAll();
        playSettingsRepository.deleteAll();
        ledgerRepository.deleteAll();
        watchProgressRepository.deleteAll();
        childProfileRepository.deleteAll();
        sceneRepository.deleteAll();
        segmentRepository.deleteAll();
        writes.executeWithoutResult(status -> entityManager.createQuery("delete from CatalogEpisodeAsset").executeUpdate());
        episodeRepository.deleteAll();
        seriesRepository.deleteAll();
        characterRepository.deleteAll();
        catalogMediaAssetRepository.deleteAll();
        mediaAssetRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.deleteAll();
        catalogReadModelCache.invalidate();
        Files.write(mediaRoot.resolve("play-ep1.mp4"), new byte[]{0, 1, 2, 3, 4, 5, 6, 7});
        Files.write(mediaRoot.resolve("play-ep2.mp4"), new byte[]{0, 1, 2, 3, 4, 5, 6, 7});
        Files.write(mediaRoot.resolve("play-ep3.mp4"), new byte[]{0, 1, 2, 3, 4, 5, 6, 7});
    }

    // test Dieppie: tăng theo heartbeat, nghỉ 2 phút tự reset, ngất khóa 423, ngủ 3 phút khóa grant/stream/progress và thuốc hồi sức giải toán
    @Test
    void enforcesWatchPetStagesPauseResetRestLockAndMedicineMathHeal() throws Exception {
        seedThreeEpisodeSeries();
        String mainToken = registerUser("petparent", "petparent@example.com", "secret123", "Ba Mẹ");
        enableParentMode(mainToken, "1234");
        long childId = createChildProfile(mainToken, "1234", "Bé Bo", "dino");
        String childToken = switchToChild(mainToken, childId);

        // chỉnh ngưỡng ngất 10 phút và thời gian ngủ 3 phút để test nhanh từng mốc
        updatePlaySettings(mainToken, childId, "1234", true, 10, 3, true, true);

        // gửi 23 nhịp * 20s = 460s (>= 75% của 600s = 450s -> SWEATY)
        sendHeartbeats(childToken, 23, 20, 15);
        mockMvc.perform(get("/api/users/play-features/status").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.watchPet.stage").value("SWEATY"))
                .andExpect(jsonPath("$.data.watchPet.continuousWatchSeconds").value(460));

        // gửi thêm 4 nhịp * 20s = 540s (90% của 600s -> DIZZY)
        sendHeartbeats(childToken, 4, 20, 15);
        mockMvc.perform(get("/api/users/play-features/status").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.watchPet.stage").value("DIZZY"));

        // dừng xem 2 phút -> tự reset về HAPPY
        clock.advance(Duration.ofSeconds(120));
        mockMvc.perform(get("/api/users/play-features/status").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.watchPet.stage").value("HAPPY"))
                .andExpect(jsonPath("$.data.watchPet.continuousWatchSeconds").value(0));

        // xem liên tục 25 nhịp * 20s = 500s rồi lấy vé phát ngay trước khi ngất (còn hạn 5 phút)
        sendHeartbeats(childToken, 25, 20, 15);
        MvcResult preGrant = mockMvc.perform(post("/api/media/grant/asset_play_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andReturn();
        String grantToken = objectMapper.readTree(preGrant.getResponse().getContentAsString()).path("grant").asText();

        // thêm 5 nhịp * 20s = đủ 600s -> Dieppie ngất (FAINTED)
        sendHeartbeats(childToken, 5, 20, 15);
        mockMvc.perform(get("/api/users/play-features/status").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.watchPet.stage").value("FAINTED"))
                .andExpect(jsonPath("$.data.watchPet.fainted").value(true));

        // khi Dieppie ngất: grant, stream và progress đều trả 423 kèm X-Dippie-Lock-Reason = WATCH_PET_FAINT
        mockMvc.perform(post("/api/media/grant/asset_play_1").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "WATCH_PET_FAINT"))
                .andExpect(jsonPath("$.message").value("Dieppie mệt xỉu rồi! Bé cho Dieppie ngủ hoặc mua thuốc hồi sức để xem tiếp nhé."));
        mockMvc.perform(get("/api/media/stream/asset_play_1").param("grant", grantToken))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "WATCH_PET_FAINT"));
        mockMvc.perform(put("/api/users/watch-progress/ep_play_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assetId", "asset_play_1", "positionMs", 30_000, "expectedVersion", -1))))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "WATCH_PET_FAINT"));

        // bé chọn "Cho Dieppie ngủ 3 phút" -> server khóa cứng 3 phút, không cho lách qua mua thuốc
        mockMvc.perform(post("/api/users/play-features/watch-pet/rest")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Dieppie đang đi ngủ nghỉ mắt rồi, bé thư giãn mắt nhé!"))
                .andExpect(jsonPath("$.data.stage").value("RESTING"))
                .andExpect(jsonPath("$.data.resting").value(true))
                .andExpect(jsonPath("$.data.restRemainingSeconds").value(180));

        mockMvc.perform(get("/api/users/play-features/watch-pet/medicine")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Dieppie đang ngủ nghỉ mắt rồi, bé chờ hết giờ nghỉ nhé."));

        mockMvc.perform(post("/api/media/grant/asset_play_1").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "WATCH_PET_REST"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.message").value("Dieppie đang ngủ nghỉ mắt rồi, bé chờ hết giờ nghỉ nhé."));
        mockMvc.perform(get("/api/media/stream/asset_play_1").param("grant", grantToken))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "WATCH_PET_REST"));
        mockMvc.perform(put("/api/users/watch-progress/ep_play_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assetId", "asset_play_1", "positionMs", 30_000, "expectedVersion", -1))))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "WATCH_PET_REST"));

        // tua đồng hồ đúng 3 phút -> hết giờ ngủ, Dieppie khỏe lại HAPPY và mở khóa grant/progress
        clock.advance(Duration.ofMinutes(3));
        mockMvc.perform(get("/api/users/play-features/status").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.watchPet.stage").value("HAPPY"))
                .andExpect(jsonPath("$.data.watchPet.resting").value(false))
                .andExpect(jsonPath("$.data.watchPet.fainted").value(false));
        mockMvc.perform(post("/api/media/grant/asset_play_1").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk());

        // xem tiếp 600s để Dieppie ngất lại, lần này chọn "Mua thuốc hồi sức cho Dieppie" bằng bài toán ký HMAC
        sendHeartbeats(childToken, 30, 20, 15);
        MvcResult challengeRes = mockMvc.perform(get("/api/users/play-features/watch-pet/medicine")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.question").isNotEmpty())
                .andReturn();
        JsonNode challenge = objectMapper.readTree(challengeRes.getResponse().getContentAsString()).path("data");
        String medToken = challenge.path("token").asText();
        int correct = solveMathQuestion(challenge.path("question").asText());

        // trả lời sai vẫn ngất
        mockMvc.perform(post("/api/users/play-features/watch-pet/medicine")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", medToken, "answer", correct + 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Chưa đúng rồi, bé thử tính lại để mua thuốc cho Dieppie nhé."))
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.watchPet.fainted").value(true));

        // trả lời đúng -> Dieppie hồi sức về HAPPY và mở khóa phát phim ngay
        mockMvc.perform(post("/api/users/play-features/watch-pet/medicine")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", medToken, "answer", correct))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Giỏi quá! Dieppie uống thuốc xong đã khỏe lại rồi."))
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.watchPet.stage").value("HAPPY"))
                .andExpect(jsonPath("$.data.watchPet.continuousWatchSeconds").value(0));

        // không cho dùng lại token đã hồi sức
        mockMvc.perform(post("/api/users/play-features/watch-pet/medicine")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", medToken, "answer", correct))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/media/grant/asset_play_1").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk());
    }

    // test rương báu bay ngang: chống spam trước ngưỡng 5-7 phút, hết hạn token, combo 3 tặng thêm +1 phút, ghi sổ cái LOOT và tôn trọng trần thưởng ngày
    @Test
    void enforcesLootDropAntiSpamExpiryComboBonusLootLedgerAndDailyBonusCap() throws Exception {
        seedThreeEpisodeSeries();
        String mainToken = registerUser("lootparent", "lootparent@example.com", "secret123", "Ba Mẹ");
        enableParentMode(mainToken, "1234");
        long childId = createChildProfile(mainToken, "1234", "Bé Na", "cat");
        // đặt trần thưởng ngày 11 phút và tắt Dieppie để tập trung test đủ 4 rương liên tiếp
        mockMvc.perform(put("/api/users/profiles/" + childId + "/screen-time")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", "1234",
                                "dailyMinutes", 120,
                                "mathLevel", 1,
                                "rewardMinutes", 5,
                                "maxBonusMinutes", 11
                        ))))
                .andExpect(status().isOk());
        updatePlaySettings(mainToken, childId, "1234", false, 20, 5, true, true);
        String childToken = switchToChild(mainToken, childId);

        // chưa xem đủ 5-7 phút -> chống spam 409
        mockMvc.perform(post("/api/users/play-features/loot/spawn")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isConflict());

        // xem đủ 420 giây (7 phút -> chắc chắn vượt ngưỡng ngẫu nhiên 300..420s)
        sendHeartbeats(childToken, 21, 20, 15);
        mockMvc.perform(get("/api/users/play-features/status").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loot.ready").value(true));

        // rương 1: spawn -> không cho spawn trùng ngay -> mở -> giải đúng -> +3 phút, combo 1
        String drop1 = spawnLoot(childToken);
        mockMvc.perform(post("/api/users/play-features/loot/spawn")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isConflict());

        JsonNode open1 = openLoot(childToken, drop1);
        // dropToken dùng 1 lần, mở lại bị 400
        mockMvc.perform(post("/api/users/play-features/loot/open")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dropToken", drop1))))
                .andExpect(status().isBadRequest());

        int ans1 = solveMathQuestion(open1.path("question").asText());
        mockMvc.perform(post("/api/users/play-features/loot/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", open1.path("token").asText(), "answer", ans1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.earnedMinutes").value(3))
                .andExpect(jsonPath("$.data.combo").value(1))
                .andExpect(jsonPath("$.data.comboBonusMinutes").value(0));

        // rương 2: đúng -> +3 phút, combo 2 (tổng thưởng 6/11 phút)
        sendHeartbeats(childToken, 21, 20, 15);
        JsonNode open2 = openLoot(childToken, spawnLoot(childToken));
        int ans2 = solveMathQuestion(open2.path("question").asText());
        mockMvc.perform(post("/api/users/play-features/loot/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", open2.path("token").asText(), "answer", ans2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.earnedMinutes").value(3))
                .andExpect(jsonPath("$.data.combo").value(2))
                .andExpect(jsonPath("$.data.comboBonusMinutes").value(0));

        // rương 3: đúng -> combo 3 thưởng +1 phút phụ = +4 phút (tổng 10/11 phút)
        sendHeartbeats(childToken, 21, 20, 15);
        JsonNode open3 = openLoot(childToken, spawnLoot(childToken));
        assertThat(open3.path("potentialComboBonusMinutes").asInt()).isEqualTo(1);
        int ans3 = solveMathQuestion(open3.path("question").asText());
        mockMvc.perform(post("/api/users/play-features/loot/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", open3.path("token").asText(), "answer", ans3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.earnedMinutes").value(4))
                .andExpect(jsonPath("$.data.combo").value(3))
                .andExpect(jsonPath("$.data.comboBonusMinutes").value(1));

        // rương 4: chỉ còn 1 phút trần thưởng ngày (11 - 10 = 1) -> server cắt đúng còn 60s và báo bonusCapReached
        sendHeartbeats(childToken, 21, 20, 15);
        JsonNode open4 = openLoot(childToken, spawnLoot(childToken));
        int ans4 = solveMathQuestion(open4.path("question").asText());
        mockMvc.perform(post("/api/users/play-features/loot/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", open4.path("token").asText(), "answer", ans4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.earnedSeconds").value(60))
                .andExpect(jsonPath("$.data.earnedMinutes").value(1))
                .andExpect(jsonPath("$.data.bonusCapReached").value(true));

        // kiểm tra sổ cái thực sự lưu dòng LOOT
        List<ScreenTimeLedgerEntry> lootRows = ledgerRepository.findAll().stream()
                .filter(entry -> entry.getKind() == ScreenTimeLedgerKind.LOOT)
                .toList();
        assertThat(lootRows).hasSize(4);
        assertThat(lootRows.stream().mapToInt(ScreenTimeLedgerEntry::getSeconds).sum()).isEqualTo(11 * 60);

        // rương 5: trả lời sai -> không cộng phút, mất combo về 0, không thử lại được cùng rương
        sendHeartbeats(childToken, 21, 20, 15);
        JsonNode open5 = openLoot(childToken, spawnLoot(childToken));
        int ans5 = solveMathQuestion(open5.path("question").asText());
        mockMvc.perform(post("/api/users/play-features/loot/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", open5.path("token").asText(), "answer", ans5 + 9))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.earnedSeconds").value(0))
                .andExpect(jsonPath("$.data.combo").value(0));
        mockMvc.perform(post("/api/users/play-features/loot/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", open5.path("token").asText(), "answer", ans5))))
                .andExpect(status().isBadRequest());

        // rương 6: quá 18 giây mới bấm mở -> hết hạn 400
        sendHeartbeats(childToken, 21, 20, 15);
        String expiredDrop = spawnLoot(childToken);
        clock.advance(Duration.ofSeconds(20));
        mockMvc.perform(post("/api/users/play-features/loot/open")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dropToken", expiredDrop))))
                .andExpect(status().isBadRequest());
    }

    // test ôn tập qua tập: hoàn thành tập 1 khóa tập 2 trên grant/stream/progress nhưng không chặn xem lại tập 1, đủ 3 kiểu quiz (cảnh, nhân vật, tên tập)
    @Test
    void enforcesRecapToNextLockUnlockAndBuildsAllThreeQuizModesFromCatalog() throws Exception {
        seedThreeEpisodeSeries();
        String mainToken = registerUser("recapparent", "recapparent@example.com", "secret123", "Ba Mẹ");
        enableParentMode(mainToken, "1234");
        long childId = createChildProfile(mainToken, "1234", "Bé Bông", "bunny");
        String childToken = switchToChild(mainToken, childId);

        // lấy trước vé phát của tập 2 để kiểm tra stream cũng bị khóa sau khi xong tập 1
        MvcResult preGrantEp2 = mockMvc.perform(post("/api/media/grant/asset_play_2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andReturn();
        String ep2GrantToken = objectMapper.readTree(preGrantEp2.getResponse().getContentAsString()).path("grant").asText();

        // bé xem xong tập 1 (position >= 95% duration 300_000ms) -> server khóa tập 2
        mockMvc.perform(put("/api/users/watch-progress/ep_play_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assetId", "asset_play_1", "positionMs", 295_000, "expectedVersion", -1))))
                .andExpect(status().isOk());

        // tập 2 bị khóa 423 ở grant, stream và progress
        mockMvc.perform(post("/api/media/grant/asset_play_2").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "RECAP_REQUIRED"));
        mockMvc.perform(get("/api/media/stream/asset_play_2").param("grant", ep2GrantToken))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "RECAP_REQUIRED"));
        mockMvc.perform(put("/api/users/watch-progress/ep_play_2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assetId", "asset_play_2", "positionMs", 15_000, "expectedVersion", -1))))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "RECAP_REQUIRED"));

        // xem lại tập 1 không bao giờ bị chặn
        mockMvc.perform(post("/api/media/grant/asset_play_1").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/users/watch-progress/ep_play_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assetId", "asset_play_1", "positionMs", 10_000, "expectedVersion", 0))))
                .andExpect(status().isOk());

        // 1) tập 1 có 4 cảnh theo thời gian -> quiz kiểu ORDER_SCENES
        MvcResult q1Res = mockMvc.perform(get("/api/users/play-features/recap/quiz")
                        .param("episodeId", "ep_play_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizType").value("ORDER_SCENES"))
                .andExpect(jsonPath("$.data.targetEpisodeId").value("ep_play_2"))
                .andExpect(jsonPath("$.data.items.length()").value(4))
                .andReturn();
        JsonNode q1 = objectMapper.readTree(q1Res.getResponse().getContentAsString()).path("data");
        String q1Token = q1.path("token").asText();

        // xếp sai thứ tự -> chưa mở khóa
        Map<String, String> idByText = new LinkedHashMap<>();
        for (JsonNode item : q1.path("items")) {
            idByText.put(item.path("text").asText(), item.path("id").asText());
        }
        List<String> correctOrder = List.of(
                idByText.get("Mèo con thức dậy chào buổi sáng"),
                idByText.get("Nhóm bạn ra công viên thả diều"),
                idByText.get("Cơn mưa bất ngờ ập xuống"),
                idByText.get("Cả nhóm chia sẻ chiếc ô về nhà")
        );
        List<String> guaranteedWrong = new ArrayList<>(correctOrder);
        java.util.Collections.reverse(guaranteedWrong);

        mockMvc.perform(post("/api/users/play-features/recap/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", q1Token, "orderedIds", guaranteedWrong))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.unlocked").value(false));

        // xếp đúng thứ tự -> mở khóa tập 2 trên server
        mockMvc.perform(post("/api/users/play-features/recap/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", q1Token, "orderedIds", correctOrder))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.unlocked").value(true));

        // tập 2 đã mở khóa: grant, stream và progress đều 200 OK
        mockMvc.perform(post("/api/media/grant/asset_play_2").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/media/stream/asset_play_2").param("grant", ep2GrantToken))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/users/watch-progress/ep_play_2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assetId", "asset_play_2", "positionMs", 295_000, "expectedVersion", -1))))
                .andExpect(status().isOk());

        // xem xong lại tập 1 cũng không bao giờ khóa lại tập 2 đã mở
        mockMvc.perform(put("/api/users/watch-progress/ep_play_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assetId", "asset_play_1", "positionMs", 296_000, "expectedVersion", 1))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/media/grant/asset_play_2").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk());

        // 2) tập 2 không có đủ cảnh nhưng có nhân vật -> quiz kiểu CHARACTER_CHOICE mở khóa tập 3
        MvcResult q2Res = mockMvc.perform(get("/api/users/play-features/recap/quiz")
                        .param("episodeId", "ep_play_2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizType").value("CHARACTER_CHOICE"))
                .andExpect(jsonPath("$.data.targetEpisodeId").value("ep_play_3"))
                .andReturn();
        JsonNode q2 = objectMapper.readTree(q2Res.getResponse().getContentAsString()).path("data");
        String correctCharId = findChoiceIdByText(q2.path("items"), "Mèo Mướp");
        assertThat(correctCharId).isNotBlank();

        mockMvc.perform(post("/api/users/play-features/recap/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", q2.path("token").asText(), "choiceId", correctCharId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(true))
                .andExpect(jsonPath("$.data.unlocked").value(true));
        mockMvc.perform(post("/api/media/grant/asset_play_3").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk());

        // 3) tập 3 ít dữ liệu (không cảnh, không phân đoạn, không nhân vật) -> fallback TITLE_CHOICE "Tập vừa rồi tên là gì?"
        MvcResult q3Res = mockMvc.perform(get("/api/users/play-features/recap/quiz")
                        .param("episodeId", "ep_play_3")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizType").value("TITLE_CHOICE"))
                .andExpect(jsonPath("$.data.prompt").value("Tập vừa rồi tên là gì?"))
                .andReturn();
        JsonNode q3 = objectMapper.readTree(q3Res.getResponse().getContentAsString()).path("data");
        String correctTitleId = findChoiceIdByText(q3.path("items"), "Tiệc bánh kem");
        assertThat(correctTitleId).isNotBlank();

        mockMvc.perform(post("/api/users/play-features/recap/answer")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", q3.path("token").asText(), "choiceId", correctTitleId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correct").value(true));
    }

    // test cài đặt phụ huynh (PIN, giới hạn, chặn 403 kid mode, tắt tính năng xả khóa ngay) và hồ sơ chính không bao giờ bị ảnh hưởng
    @Test
    void managesParentPlaySettingsAndKeepsMainProfileUnaffected() throws Exception {
        seedThreeEpisodeSeries();
        String mainToken = registerUser("mainfamily", "mainfamily@example.com", "secret123", "Gia Đình");

        // tài khoản chưa bật parent mode -> status inactive, xem xong tập 1 không khóa tập 2
        mockMvc.perform(get("/api/users/play-features/status").header(HttpHeaders.AUTHORIZATION, bearer(mainToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
        mockMvc.perform(post("/api/users/play-features/watch-pet/rest").header(HttpHeaders.AUTHORIZATION, bearer(mainToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/users/watch-progress/ep_play_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assetId", "asset_play_1", "positionMs", 295_000, "expectedVersion", -1))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/media/grant/asset_play_2").header(HttpHeaders.AUTHORIZATION, bearer(mainToken)))
                .andExpect(status().isOk());

        enableParentMode(mainToken, "4321");
        long childId = createChildProfile(mainToken, "4321", "Bé Sóc", "fox");
        String childToken = switchToChild(mainToken, childId);

        // hồ sơ trẻ em bị chặn 403 khi gọi cài đặt phụ huynh
        mockMvc.perform(get("/api/users/profiles/play-features").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/users/profiles/" + childId + "/play-features")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", "4321",
                                "watchPetEnabled", false,
                                "watchPetFaintMinutes", 20,
                                "watchPetRestMinutes", 5,
                                "lootDropEnabled", false,
                                "recapToNextEnabled", false
                        ))))
                .andExpect(status().isForbidden());

        // kiểm tra ràng buộc khoảng phút và mã PIN
        mockMvc.perform(put("/api/users/profiles/" + childId + "/play-features")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", "9999",
                                "watchPetEnabled", true,
                                "watchPetFaintMinutes", 20,
                                "watchPetRestMinutes", 5,
                                "lootDropEnabled", true,
                                "recapToNextEnabled", true
                        ))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/users/profiles/" + childId + "/play-features")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", "4321",
                                "watchPetEnabled", true,
                                "watchPetFaintMinutes", 5,
                                "watchPetRestMinutes", 5,
                                "lootDropEnabled", true,
                                "recapToNextEnabled", true
                        ))))
                .andExpect(status().isBadRequest());

        // khóa tập 2 bằng recap trước rồi phụ huynh tắt recap + Dieppie + rương báu -> lập tức mở lại bình thường
        mockMvc.perform(put("/api/users/watch-progress/ep_play_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assetId", "asset_play_1", "positionMs", 295_000, "expectedVersion", -1))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/media/grant/asset_play_2").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isLocked());

        updatePlaySettings(mainToken, childId, "4321", false, 30, 8, false, false);

        mockMvc.perform(get("/api/users/profiles/play-features").header(HttpHeaders.AUTHORIZATION, bearer(mainToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].watchPetEnabled").value(false))
                .andExpect(jsonPath("$.data[0].watchPetFaintMinutes").value(30))
                .andExpect(jsonPath("$.data[0].watchPetRestMinutes").value(8))
                .andExpect(jsonPath("$.data[0].lootDropEnabled").value(false))
                .andExpect(jsonPath("$.data[0].recapToNextEnabled").value(false));

        mockMvc.perform(post("/api/media/grant/asset_play_2").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users/play-features/loot/spawn").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isBadRequest());
    }

    // test hồ sơ không giới hạn phút ngày bỏ qua khóa hạn mức nhưng vẫn chạy Dieppie và cổng ôn tập
    @Test
    void enforcesWatchPetAndRecapLocksForUnlimitedChildProfileWithoutBudgetLock() throws Exception {
        seedThreeEpisodeSeries();
        String mainToken = registerUser("unlimmom", "unlimmom@example.com", "secret123", "Mẹ An");
        enableParentMode(mainToken, "1234");
        MvcResult childRes = mockMvc.perform(post("/api/users/profiles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "1234", "name", "Bé Bi", "avatarKey", "bear", "unlimited", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.unlimited").value(true))
                .andReturn();
        long childId = objectMapper.readTree(childRes.getResponse().getContentAsString()).path("data").path("id").asLong();
        updatePlaySettings(mainToken, childId, "1234", true, 10, 3, true, true);
        String childToken = switchToChild(mainToken, childId);

        // hoàn thành tập 1 vẫn khóa tập 2 bằng recap dù không giới hạn giờ xem
        mockMvc.perform(put("/api/users/watch-progress/ep_play_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assetId", "asset_play_1", "positionMs", 295_000, "expectedVersion", -1))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/media/grant/asset_play_2").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "RECAP_REQUIRED"));

        // gửi đủ 30 nhịp * 20s = 600s -> không ghi WATCH vào sổ cái, không khóa hạn mức, nhưng Dieppie ngất và khóa 423
        sendHeartbeats(childToken, 30, 20, 15);
        assertThat(ledgerRepository.count()).isZero();
        mockMvc.perform(get("/api/users/screen-time").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unlimited").value(true))
                .andExpect(jsonPath("$.data.locked").value(false));
        mockMvc.perform(post("/api/media/grant/asset_play_1").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isLocked())
                .andExpect(header().string("X-Dippie-Lock-Reason", "WATCH_PET_FAINT"));
    }

    private void sendHeartbeats(String childToken, int count, int secondsEach, int stepSeconds) throws Exception {
        for (int i = 0; i < count; i++) {
            clock.advance(Duration.ofSeconds(stepSeconds));
            mockMvc.perform(post("/api/users/screen-time/heartbeat")
                            .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("seconds", secondsEach))))
                    .andExpect(status().isOk());
        }
    }

    private String spawnLoot(String childToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/play-features/loot/spawn")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dropToken").isNotEmpty())
                .andExpect(jsonPath("$.data.visibleSeconds").value(15))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("dropToken").asText();
    }

    private JsonNode openLoot(String childToken, String dropToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/play-features/loot/open")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dropToken", dropToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timerSeconds").value(10))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private void updatePlaySettings(
            String mainToken,
            long childId,
            String pin,
            boolean watchPetEnabled,
            int faintMinutes,
            int restMinutes,
            boolean lootEnabled,
            boolean recapEnabled
    ) throws Exception {
        mockMvc.perform(put("/api/users/profiles/" + childId + "/play-features")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "pin", pin,
                                "watchPetEnabled", watchPetEnabled,
                                "watchPetFaintMinutes", faintMinutes,
                                "watchPetRestMinutes", restMinutes,
                                "lootDropEnabled", lootEnabled,
                                "recapToNextEnabled", recapEnabled
                        ))))
                .andExpect(status().isOk());
    }

    private static String findChoiceIdByText(JsonNode items, String expectedText) {
        for (JsonNode item : items) {
            if (expectedText.equals(item.path("text").asText())) {
                return item.path("id").asText();
            }
        }
        return "";
    }

    private static int solveMathQuestion(String question) {
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

    private void seedThreeEpisodeSeries() {
        movieRepository.saveAllAndFlush(List.of(
                new Movie("ser_play", "Chú Mèo Nhỏ", 2025, 9.0, "15 phút", "[\"Family\"]", "Phim cho bé", null, null, null),
                new Movie("ep_play_1", "Buổi sáng thả diều", 2025, 9.0, "5 phút", "[\"Family\"]", "Tập 1", null, null, null),
                new Movie("ep_play_2", "Bí mật nhà kho", 2025, 9.0, "5 phút", "[\"Family\"]", "Tập 2", null, null, null),
                new Movie("ep_play_3", "Tiệc bánh kem", 2025, 9.0, "5 phút", "[\"Family\"]", "Tập 3", null, null, null)
        ));

        seriesRepository.saveAndFlush(new CatalogSeries(
                "ser_play", "Chú Mèo Nhỏ", null, null, null, null, "[\"family\"]", null, null, null, null,
                2025, null, null, "[\"char_meo\",\"char_cun\",\"char_tho\",\"char_gau\"]", null, null, null, null, null, null,
                READY_JSON, "{\"id\":\"ser_play\"}", 1
        ));

        characterRepository.saveAllAndFlush(List.of(
                new CatalogCharacter("char_meo", "[\"ser_play\"]", "Mèo Mướp", null, "Mèo tinh nghịch", "main", null, null, null, null, READY_JSON, "{}", 1),
                new CatalogCharacter("char_cun", "[\"ser_play\"]", "Cún Vàng", null, "Cún vui tính", "supporting", null, null, null, null, READY_JSON, "{}", 1),
                new CatalogCharacter("char_tho", "[\"ser_play\"]", "Thỏ Trắng", null, "Thỏ nhanh nhẹn", "supporting", null, null, null, null, READY_JSON, "{}", 1),
                new CatalogCharacter("char_gau", "[\"ser_play\"]", "Gấu Nâu", null, "Gấu hiền lành", "supporting", null, null, null, null, READY_JSON, "{}", 1)
        ));

        for (int i = 1; i <= 3; i++) {
            String assetId = "asset_play_" + i;
            String epId = "ep_play_" + i;
            String file = "play-ep" + i + ".mp4";
            catalogMediaAssetRepository.saveAndFlush(new CatalogMediaAsset(
                    assetId, "video", "feature", "Bản chuẩn", null, null,
                    "local", "local", file, null, null,
                    8L, null, 300_000L, null,
                    "[{\"episode_id\":\"" + epId + "\",\"scope\":\"full_episode\"}]",
                    null, "authenticated", true, READY_JSON, "{\"id\":\"" + assetId + "\"}", 1
            ));
            mediaAssetRepository.saveAndFlush(new MediaAsset(
                    assetId, epId, "feature", "local", "local",
                    file, "v1", "video/mp4", 8L, 300.0, false, true
            ));
        }

        episodeRepository.saveAllAndFlush(List.of(
                new CatalogEpisode(
                        "ep_play_1", "ser_play", null, 1, "Buổi sáng thả diều", null, null, null, "regular", null,
                        300_000L, null, null, null, null, "[\"char_meo\",\"char_cun\"]", null, null, null, null, null, null, null,
                        READY_JSON, "{\"id\":\"ep_play_1\"}", 1
                ),
                new CatalogEpisode(
                        "ep_play_2", "ser_play", null, 2, "Bí mật nhà kho", null, null, null, "regular", null,
                        300_000L, null, null, null, null, "[\"char_meo\"]", null, null, null, null, null, null, null,
                        READY_JSON, "{\"id\":\"ep_play_2\"}", 1
                ),
                new CatalogEpisode(
                        "ep_play_3", "ser_play", null, 3, "Tiệc bánh kem", null, null, null, "regular", null,
                        300_000L, null, null, null, null, null, null, null, null, null, null, null, null,
                        READY_JSON, "{\"id\":\"ep_play_3\"}", 1
                )
        ));

        writes.executeWithoutResult(status -> {
            entityManager.persist(new CatalogEpisodeAsset("ep_play_1", "asset_play_1"));
            entityManager.persist(new CatalogEpisodeAsset("ep_play_2", "asset_play_2"));
            entityManager.persist(new CatalogEpisodeAsset("ep_play_3", "asset_play_3"));
        });

        sceneRepository.saveAllAndFlush(List.of(
                new CatalogScene("scn_1", "asset_play_1", "ep_play_1", null, 10_000L, 40_000L, "Cảnh 1",
                        "Mèo con thức dậy chào buổi sáng", null, null, null, null, null, null, null, null, null, READY_JSON, "{}", 1),
                new CatalogScene("scn_2", "asset_play_1", "ep_play_1", null, 60_000L, 120_000L, "Cảnh 2",
                        "Nhóm bạn ra công viên thả diều", null, null, null, null, null, null, null, null, null, READY_JSON, "{}", 1),
                new CatalogScene("scn_3", "asset_play_1", "ep_play_1", null, 150_000L, 210_000L, "Cảnh 3",
                        "Cơn mưa bất ngờ ập xuống", null, null, null, null, null, null, null, null, null, READY_JSON, "{}", 1),
                new CatalogScene("scn_4", "asset_play_1", "ep_play_1", null, 230_000L, 290_000L, "Cảnh 4",
                        "Cả nhóm chia sẻ chiếc ô về nhà", null, null, null, null, null, null, null, null, null, READY_JSON, "{}", 1)
        ));

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

    // đồng hồ giả cho bài test tua nhịp tim, hết hạn rương báu và hết giờ nghỉ mắt
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
