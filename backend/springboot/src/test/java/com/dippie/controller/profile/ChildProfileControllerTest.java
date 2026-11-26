package com.dippie.controller.profile;

import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.movie.Movie;
import com.dippie.model.user.Role;
import com.dippie.model.user.User;
import com.dippie.repository.catalog.CatalogEpisodeRepository;
import com.dippie.repository.catalog.CatalogSeriesRepository;
import com.dippie.repository.library.FavoriteItemRepository;
import com.dippie.repository.library.MovieRatingRepository;
import com.dippie.repository.library.WatchlistItemRepository;
import com.dippie.repository.movie.MovieRepository;
import com.dippie.repository.profile.ChildProfileRepository;
import com.dippie.repository.progress.WatchProgressRepository;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.JwtService;
import com.dippie.security.LoginRateLimiter;
import com.dippie.service.catalog.CatalogReadModelCache;
import com.dippie.service.profile.ParentPinRateLimiter;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// test trọn luồng chế độ phụ huynh, hồ sơ trẻ em, đổi token JWT, tách dữ liệu theo hồ sơ và chặn 403 kid mode
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ChildProfileControllerTest {

    private static final String READY_JSON = "{\"status\":\"ready\"}";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final ChildProfileRepository childProfileRepository;
    private final MovieRepository movieRepository;
    private final CatalogSeriesRepository seriesRepository;
    private final CatalogEpisodeRepository episodeRepository;
    private final WatchProgressRepository watchProgressRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final FavoriteItemRepository favoriteItemRepository;
    private final MovieRatingRepository movieRatingRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;
    private final ParentPinRateLimiter parentPinRateLimiter;
    private final CatalogReadModelCache catalogReadModelCache;

    ChildProfileControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            ChildProfileRepository childProfileRepository,
            MovieRepository movieRepository,
            CatalogSeriesRepository seriesRepository,
            CatalogEpisodeRepository episodeRepository,
            WatchProgressRepository watchProgressRepository,
            WatchlistItemRepository watchlistItemRepository,
            FavoriteItemRepository favoriteItemRepository,
            MovieRatingRepository movieRatingRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginRateLimiter loginRateLimiter,
            ParentPinRateLimiter parentPinRateLimiter,
            CatalogReadModelCache catalogReadModelCache
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.childProfileRepository = childProfileRepository;
        this.movieRepository = movieRepository;
        this.seriesRepository = seriesRepository;
        this.episodeRepository = episodeRepository;
        this.watchProgressRepository = watchProgressRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.favoriteItemRepository = favoriteItemRepository;
        this.movieRatingRepository = movieRatingRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginRateLimiter = loginRateLimiter;
        this.parentPinRateLimiter = parentPinRateLimiter;
        this.catalogReadModelCache = catalogReadModelCache;
    }

    @BeforeEach
    void setUp() {
        loginRateLimiter.reset();
        parentPinRateLimiter.reset();
        watchProgressRepository.deleteAll();
        watchlistItemRepository.deleteAll();
        favoriteItemRepository.deleteAll();
        movieRatingRepository.deleteAll();
        childProfileRepository.deleteAll();
        episodeRepository.deleteAll();
        seriesRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.deleteAll();
        catalogReadModelCache.invalidate();
    }

    // tài khoản chưa bật chế độ phụ huynh phải trả JSON y hệt trước và lưu profile_id là null
    @Test
    void accountWithoutParentModeBehavesExactlyAsBefore() throws Exception {
        seedPlayableCatalog();
        String token = registerUser("plain", "plain@example.com", "secret123", "Plain Viewer");

        // JWT đăng nhập thường không có claim pid
        assertThat(jwtService.parseLoginToken(token)).isPresent();
        assertThat(jwtService.parseLoginToken(token).orElseThrow().profileId()).isNull();

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("plain"))
                .andExpect(jsonPath("$.data.email").value("plain@example.com"))
                .andExpect(jsonPath("$.data.fullName").value("Plain Viewer"))
                .andExpect(jsonPath("$.data.role").value("ROLE_USER"));

        mockMvc.perform(get("/api/users/profiles").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentModeEnabled").value(false))
                .andExpect(jsonPath("$.data.activeProfileId").isEmpty())
                .andExpect(jsonPath("$.data.activeProfile").isEmpty())
                .andExpect(jsonPath("$.data.profiles.length()").value(0));

        // chưa bật parent mode thì tạo hồ sơ bé bị chặn 400
        mockMvc.perform(post("/api/users/profiles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "1234", "name", "Bé Bi", "avatarKey", "bear"))))
                .andExpect(status().isBadRequest());

        // thao tác thư viện với tiến độ vẫn chạy bình thường và để profileId = null
        mockMvc.perform(post("/api/users/watchlist")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("movieId", "mov_comedy"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("movieId", "mov_comedy"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users/ratings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("movieId", "mov_comedy", "rating", 9))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/users/watch-progress/ep_kid_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assetId", "youtube:abcdefghijk", "positionMs", 30_000, "expectedVersion", -1))))
                .andExpect(status().isOk());

        User plain = userRepository.findByUsername("plain").orElseThrow();
        assertThat(plain.isParentModeEnabled()).isFalse();
        assertThat(plain.getParentPinHash()).isNull();
        assertThat(watchlistItemRepository.findByUserIdAndMovieId(plain.getId(), "mov_comedy").orElseThrow().getProfileId()).isNull();
        assertThat(favoriteItemRepository.findByUserIdAndMovieId(plain.getId(), "mov_comedy").orElseThrow().getProfileId()).isNull();
        assertThat(movieRatingRepository.findByUserIdAndMovieId(plain.getId(), "mov_comedy").orElseThrow().getProfileId()).isNull();
        assertThat(watchProgressRepository.findByUserIdAndEpisodeId(plain.getId(), "ep_kid_1").orElseThrow().getProfileId()).isNull();
    }

    // test bật/đổi/tắt PIN BCrypt, sai 5 lần bị khóa 429 và tắt chế độ thì giữ hồ sơ nhưng cắt token bé ngay
    @Test
    void managesParentModePinHashingLockoutAndDisableKeepProfiles() throws Exception {
        String token = registerUser("parent", "parent@example.com", "secret123", "Parent One");

        // chặn mấy mã PIN sai định dạng
        for (String badPin : List.of("", "123", "1234567", "12ab", " 1234")) {
            mockMvc.perform(post("/api/users/parent-mode/enable")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("pin", badPin))))
                    .andExpect(status().isBadRequest());
        }

        // bật chế độ phụ huynh với PIN 4 số
        mockMvc.perform(post("/api/users/parent-mode/enable")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "2468"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.profileCount").value(0));

        // bật trùng lần 2 trả 409
        mockMvc.perform(post("/api/users/parent-mode/enable")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "2468"))))
                .andExpect(status().isConflict());

        // mã PIN dưới DB phải băm BCrypt chứ không lưu chữ thô
        User stored = userRepository.findByUsername("parent").orElseThrow();
        assertThat(stored.isParentModeEnabled()).isTrue();
        assertThat(stored.getParentPinHash()).isNotEqualTo("2468");
        assertThat(passwordEncoder.matches("2468", stored.getParentPinHash())).isTrue();

        // tạo 1 hồ sơ bé rồi chuyển sang lấy child token
        long childId = createChildProfile(token, "2468", "Bé Bông", "bunny");
        String childToken = switchToChild(token, childId);

        // đổi PIN từ 2468 sang 135790
        mockMvc.perform(put("/api/users/parent-mode/pin")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPin", "2468", "newPin", "2468"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/users/parent-mode/pin")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPin", "2468", "newPin", "135790"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        // thử sai 5 lần liên tiếp -> lần thứ 6 kể cả nhập đúng PIN vẫn bị khóa 429 kèm Retry-After
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/users/parent-mode/disable")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("pin", "2468"))))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/users/parent-mode/disable")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "135790"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        // xả khóa để test tắt chế độ bằng PIN mới
        parentPinRateLimiter.reset();
        mockMvc.perform(post("/api/users/parent-mode/disable")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "135790"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.profileCount").value(1));

        // tắt parent mode là token hồ sơ bé chết liền 401 còn hồ sơ bé vẫn giữ lại trong DB
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/profiles").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentModeEnabled").value(false))
                .andExpect(jsonPath("$.data.profiles.length()").value(0));
        assertThat(childProfileRepository.countByUserId(stored.getId())).isEqualTo(1L);

        // bật lại parent mode là hồ sơ bé cũ hiện ra lại đầy đủ
        mockMvc.perform(post("/api/users/parent-mode/enable")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "9999"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileCount").value(1));
        mockMvc.perform(get("/api/users/profiles").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profiles.length()").value(1))
                .andExpect(jsonPath("$.data.profiles[0].name").value("Bé Bông"));
    }

    // test CRUD hồ sơ bé: trần 6 hồ sơ, check tên/avatar, cần PIN, xóa sạch dữ liệu bé và chặn đụng hồ sơ nhà khác
    @Test
    void enforcesChildProfileCrudLimitsPinAndCrossAccountIsolation() throws Exception {
        seedPlayableCatalog();
        String aliceToken = registerUser("alice", "alice@example.com", "secret123", "Alice");
        String bobToken = registerUser("bob", "bob@example.com", "secret123", "Bob");
        enableParentMode(aliceToken, "1234");
        enableParentMode(bobToken, "5678");

        // sai avatarKey hoặc tên trống đều trả 400
        mockMvc.perform(post("/api/users/profiles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "1234", "name", "Bé A", "avatarKey", "dragon"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/users/profiles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "1234", "name", "   ", "avatarKey", "bear"))))
                .andExpect(status().isBadRequest());

        // tạo đủ 6 hồ sơ thì cái thứ 7 bị chặn
        long firstChildId = createChildProfile(aliceToken, "1234", "Bé 1", "bear");
        createChildProfile(aliceToken, "1234", "Bé 2", "bunny");
        createChildProfile(aliceToken, "1234", "Bé 3", "cat");
        createChildProfile(aliceToken, "1234", "Bé 4", "dino");
        createChildProfile(aliceToken, "1234", "Bé 5", "fox");
        createChildProfile(aliceToken, "1234", "Bé 6", "owl");

        mockMvc.perform(post("/api/users/profiles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "1234", "name", "Bé 7", "avatarKey", "star"))))
                .andExpect(status().isBadRequest());

        // sửa hồ sơ cần đúng PIN
        mockMvc.perform(put("/api/users/profiles/" + firstChildId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "0000", "name", "Đổi Sai", "avatarKey", "panda"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/users/profiles/" + firstChildId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "1234", "name", "Bé Gấu", "avatarKey", "panda"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Bé Gấu"))
                .andExpect(jsonPath("$.data.avatarKey").value("panda"));

        // bob không được sửa, chuyển hay xóa hồ sơ của alice
        mockMvc.perform(put("/api/users/profiles/" + firstChildId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "5678", "name", "Trộm", "avatarKey", "cat"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/users/profiles/" + firstChildId + "/switch")
                        .header(HttpHeaders.AUTHORIZATION, bearer(bobToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/users/profiles/" + firstChildId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(bobToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "5678"))))
                .andExpect(status().isNotFound());

        // lưu dữ liệu cho hồ sơ bé 1 rồi xóa hồ sơ bé 1 -> dữ liệu bé bay sạch, token bé chết 401
        String childToken = switchToChild(aliceToken, firstChildId);
        mockMvc.perform(post("/api/users/watchlist")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("movieId", "mov_comedy"))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/users/profiles/" + firstChildId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aliceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "1234"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isUnauthorized());
        assertThat(watchlistItemRepository.count()).isZero();
    }

    // test chuyển hồ sơ qua lại bằng JWT, thu hồi qua tokenVersion khi logout và xóa tài khoản thì bay hết hồ sơ bé
    @Test
    void switchesProfilesWithJwtRespectsTokenVersionAndDeletesProfilesWithAccount() throws Exception {
        String mainToken = registerUser("family", "family@example.com", "secret123", "Gia Đình");
        enableParentMode(mainToken, "4321");
        long childId = createChildProfile(mainToken, "4321", "Bé Na", "cat");

        // chuyển sang hồ sơ bé không cần PIN, token mới mang claim pid
        MvcResult switchResult = mockMvc.perform(post("/api/users/profiles/" + childId + "/switch")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.type").value("Bearer"))
                .andExpect(jsonPath("$.data.activeProfileId").value(childId))
                .andExpect(jsonPath("$.data.activeProfile.name").value("Bé Na"))
                .andReturn();
        String childToken = extractToken(switchResult);
        assertThat(jwtService.parseLoginToken(childToken).orElseThrow().profileId()).isEqualTo(childId);

        // thoát hồ sơ trẻ em sai PIN bị chặn 400, đúng PIN trả token chính không còn pid
        mockMvc.perform(post("/api/users/profiles/exit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "9999"))))
                .andExpect(status().isBadRequest());

        MvcResult exitResult = mockMvc.perform(post("/api/users/profiles/exit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(childToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "4321"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.activeProfileId").isEmpty())
                .andExpect(jsonPath("$.data.activeProfile").isEmpty())
                .andReturn();
        String backToMainToken = extractToken(exitResult);
        assertThat(jwtService.parseLoginToken(backToMainToken).orElseThrow().profileId()).isNull();

        // logout tăng tokenVersion phải thu hồi cả token chính lẫn token bé
        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(backToMainToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(childToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(backToMainToken)))
                .andExpect(status().isUnauthorized());

        // đăng nhập lại rồi xóa tài khoản -> hồ sơ bé cũng bị xóa theo
        MvcResult relogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("usernameOrEmail", "family", "password", "secret123"))))
                .andExpect(status().isOk())
                .andReturn();
        String freshMainToken = extractToken(relogin);

        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(freshMainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "secret123"))))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByUsername("family")).isEmpty();
        assertThat(childProfileRepository.findAll()).isEmpty();
    }

    // test tách biệt toàn bộ dữ liệu giữa hồ sơ chính, bé 1 và bé 2: xem tiếp, next-episode, xóa lịch sử, tủ phim, đánh giá, gợi ý
    @Test
    void isolatesWatchProgressNextEpisodeHistoryClearLibraryRatingsAndRecommendationsPerProfile() throws Exception {
        seedPlayableCatalog();
        String mainToken = registerUser("home", "home@example.com", "secret123", "Nhà Mèo");
        enableParentMode(mainToken, "1111");
        long kid1Id = createChildProfile(mainToken, "1111", "Bé Cún", "fox");
        long kid2Id = createChildProfile(mainToken, "1111", "Bé Sóc", "owl");
        String kid1Token = switchToChild(mainToken, kid1Id);
        String kid2Token = switchToChild(mainToken, kid2Id);

        // 1. watchlist, favorites, ratings tách biệt kể cả khi cùng chấm chung 1 bộ phim
        mockMvc.perform(post("/api/users/watchlist")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("movieId", "mov_comedy"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users/watchlist")
                        .header(HttpHeaders.AUTHORIZATION, bearer(kid1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("movieId", "mov_comedy"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users/watchlist")
                        .header(HttpHeaders.AUTHORIZATION, bearer(kid1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("movieId", "mov_adventure"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/watchlist").header(HttpHeaders.AUTHORIZATION, bearer(mainToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/users/watchlist").header(HttpHeaders.AUTHORIZATION, bearer(kid1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/users/watchlist").header(HttpHeaders.AUTHORIZATION, bearer(kid2Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // favorites và ratings dùng làm tín hiệu gợi ý: main thích hài (mov_comedy), kid1 thích phiêu lưu (mov_adventure)
        mockMvc.perform(post("/api/users/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("movieId", "mov_comedy"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users/favorites")
                        .header(HttpHeaders.AUTHORIZATION, bearer(kid1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("movieId", "mov_adventure"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/users/ratings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("movieId", "mov_comedy", "rating", 9))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users/ratings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(kid1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("movieId", "mov_comedy", "rating", 3))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/users/ratings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(kid1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("movieId", "mov_adventure", "rating", 10))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/ratings").header(HttpHeaders.AUTHORIZATION, bearer(mainToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].rating").value(9));
        mockMvc.perform(get("/api/users/ratings").header(HttpHeaders.AUTHORIZATION, bearer(kid1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(get("/api/users/ratings").header(HttpHeaders.AUTHORIZATION, bearer(kid2Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // 2. watch progress, Xem tiếp và next-episode tách riêng theo hồ sơ
        // main xem xong tập 1 -> next-episode là tập 2
        mockMvc.perform(put("/api/users/watch-progress/ep_kid_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assetId", "youtube:abcdefghijk", "positionMs", 295_000, "expectedVersion", -1))))
                .andExpect(status().isOk());
        // kid1 đang xem dở tập 1 ở 40s -> next-episode là RESUME tập 1
        mockMvc.perform(put("/api/users/watch-progress/ep_kid_1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(kid1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("assetId", "youtube:abcdefghijk", "positionMs", 40_000, "expectedVersion", -1))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/next-episode?seriesId=ser_kid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.episodeId").value("ep_kid_2"))
                .andExpect(jsonPath("$.data.reason").value("NEXT_UNWATCHED"));

        mockMvc.perform(get("/api/users/next-episode?seriesId=ser_kid")
                        .header(HttpHeaders.AUTHORIZATION, bearer(kid1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.episodeId").value("ep_kid_1"))
                .andExpect(jsonPath("$.data.reason").value("RESUME"))
                .andExpect(jsonPath("$.data.resumePositionMs").value(40_000));

        mockMvc.perform(get("/api/users/watch-progress")
                        .header(HttpHeaders.AUTHORIZATION, bearer(kid2Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // 3. xóa lịch sử xem trong kid1 chỉ xóa lịch sử của kid1, main vẫn còn nguyên
        mockMvc.perform(delete("/api/users/watch-progress")
                        .header(HttpHeaders.AUTHORIZATION, bearer(kid1Token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/watch-progress").header(HttpHeaders.AUTHORIZATION, bearer(kid1Token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/users/watch-progress").header(HttpHeaders.AUTHORIZATION, bearer(mainToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].episodeId").value("ep_kid_1"));

        // 4. gợi ý phim đọc đúng tín hiệu từng hồ sơ: main còn tiến độ ep_kid_1 nên đẩy ep_kid_2 lên đầu kèm mov_comedy_2, còn kid1 đẩy mov_adventure_2 lên đầu
        mockMvc.perform(post("/api/recommend")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("limit", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedMovies[0].id").value("ep_kid_2"))
                .andExpect(jsonPath("$.recommendedMovies[1].id").value("mov_comedy_2"));

        mockMvc.perform(post("/api/recommend")
                        .header(HttpHeaders.AUTHORIZATION, bearer(kid1Token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("limit", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedMovies[0].id").value("mov_adventure_2"));
    }

    // test chế độ trẻ em chặn 403 kèm câu tiếng Việt ở toàn bộ cài đặt tài khoản, cài đặt phụ huynh và /api/admin/** kể cả khi tài khoản gốc là admin
    @Test
    void refusesAccountParentSettingsAndAdminRoutesWith403ForChildProfileToken() throws Exception {
        User admin = userRepository.saveAndFlush(new User(
                "adminparent",
                "adminparent@dippie.vn",
                passwordEncoder.encode("AdminPass123!"),
                "Admin Parent",
                Role.ROLE_ADMIN,
                LocalDateTime.of(2026, 11, 26, 10, 0)
        ));
        String adminMainToken = jwtService.issueLoginToken(admin.getUsername(), admin.getTokenVersion());
        enableParentMode(adminMainToken, "2026");
        long childId = createChildProfile(adminMainToken, "2026", "Bé Admin", "dino");
        String adminChildToken = switchToChild(adminMainToken, childId);

        // tài khoản admin đang ở hồ sơ trẻ em vẫn bị chặn 403 trên mọi route admin
        mockMvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(adminChildToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Hồ sơ trẻ em không thể truy cập khu vực quản trị."));
        mockMvc.perform(get("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, bearer(adminChildToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Hồ sơ trẻ em không thể truy cập khu vực quản trị."));
        mockMvc.perform(get("/api/admin/audit-events").header(HttpHeaders.AUTHORIZATION, bearer(adminChildToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reports/playback").header(HttpHeaders.AUTHORIZATION, bearer(adminChildToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/catalog/history").header(HttpHeaders.AUTHORIZATION, bearer(adminChildToken)))
                .andExpect(status().isForbidden());

        // /api/assistant/status không lộ thông tin chỉ dành cho admin khi đang ở hồ sơ bé
        mockMvc.perform(get("/api/assistant/status").header(HttpHeaders.AUTHORIZATION, bearer(adminChildToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documents").isEmpty())
                .andExpect(jsonPath("$.data.llmProvider").isEmpty());

        // chặn 403 sửa tài khoản, đổi mật khẩu, xóa tài khoản
        mockMvc.perform(put("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminChildToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Hack", "email", "hack@example.com"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Hồ sơ trẻ em không thể thay đổi cài đặt tài khoản."));

        mockMvc.perform(post("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminChildToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "AdminPass123!", "newPassword", "NewAdminPass456!"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Hồ sơ trẻ em không thể đổi mật khẩu."));

        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminChildToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "AdminPass123!"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Hồ sơ trẻ em không thể xóa tài khoản."));

        // chặn 403 cài đặt phụ huynh và CRUD hồ sơ trẻ em
        mockMvc.perform(get("/api/users/parent-mode").header(HttpHeaders.AUTHORIZATION, bearer(adminChildToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Hồ sơ trẻ em không thể chỉnh cài đặt phụ huynh."));
        mockMvc.perform(post("/api/users/parent-mode/enable")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminChildToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "9999"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/users/parent-mode/pin")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminChildToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPin", "2026", "newPin", "9999"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users/parent-mode/disable")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminChildToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "2026"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users/profiles")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminChildToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "2026", "name", "Lén", "avatarKey", "star"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Hồ sơ trẻ em không thể quản lý hồ sơ."));
        mockMvc.perform(put("/api/users/profiles/" + childId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminChildToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "2026", "name", "Lén", "avatarKey", "star"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/users/profiles/" + childId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminChildToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pin", "2026"))))
                .andExpect(status().isForbidden());

        // còn GET /api/users/profiles vẫn mở để giao diện hiện đúng bé đang xem
        mockMvc.perform(get("/api/users/profiles").header(HttpHeaders.AUTHORIZATION, bearer(adminChildToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeProfileId").value(childId))
                .andExpect(jsonPath("$.data.activeProfile.name").value("Bé Admin"));
    }

    private void seedPlayableCatalog() {
        Movie comedy1 = new Movie("mov_comedy", "Hài Tập 1", 2024, 8.0, "10 phút", "[\"Comedy\"]", "Phim hài vui nhộn", null, null, null);
        Movie comedy2 = new Movie("mov_comedy_2", "Hài Tập 2", 2024, 8.2, "10 phút", "[\"Comedy\"]", "Phim hài kế tiếp", null, null, null);
        Movie adv1 = new Movie("mov_adventure", "Phiêu Lưu 1", 2024, 8.0, "10 phút", "[\"Adventure\"]", "Phim phiêu lưu rừng xanh", null, null, null);
        Movie adv2 = new Movie("mov_adventure_2", "Phiêu Lưu 2", 2024, 8.2, "10 phút", "[\"Adventure\"]", "Phim phiêu lưu đại dương", null, null, null);
        Movie seriesMovie = new Movie("ser_kid", "Hoạt Hình Gấu", 2024, 8.5, "10 phút", "[\"Family\"]", "Bộ phim cho bé", null, null, null);
        Movie ep1Movie = new Movie("ep_kid_1", "Tập 1", 2024, 8.5, "5 phút", "[\"Family\"]", "Tập mở đầu", null, null, null);
        Movie ep2Movie = new Movie("ep_kid_2", "Tập 2", 2024, 8.5, "5 phút", "[\"Family\"]", "Tập tiếp theo", null, null, null);
        movieRepository.saveAllAndFlush(List.of(comedy1, comedy2, adv1, adv2, seriesMovie, ep1Movie, ep2Movie));

        seriesRepository.saveAllAndFlush(List.of(
                new CatalogSeries(
                        "ser_kid", "Hoạt Hình Gấu", null, null, null, null, "[\"family\"]", null, null, null, null,
                        2024, null, null, null, null, null, null, null, null, null, READY_JSON, "{\"id\":\"ser_kid\"}", 1
                ),
                new CatalogSeries(
                        "ser_comedy", "Phim Hài Vui", null, null, null, null, "[\"comedy\"]", null, null, null, null,
                        2024, null, null, null, null, null, null, null, null, null, READY_JSON, "{\"id\":\"ser_comedy\"}", 1
                ),
                new CatalogSeries(
                        "ser_adventure", "Phiêu Lưu Nhí", null, null, null, null, "[\"adventure\"]", null, null, null, null,
                        2024, null, null, null, null, null, null, null, null, null, READY_JSON, "{\"id\":\"ser_adventure\"}", 1
                )
        ));

        CatalogEpisode ep1 = new CatalogEpisode(
                "ep_kid_1", "ser_kid", null, 1, "Tập 1", null, null, null, "regular", null,
                300_000L, null, null, null, null, null, null, null, null, null, null, null, null,
                READY_JSON,
                "{\"id\":\"ep_kid_1\",\"external_ids\":[{\"namespace\":\"youtube_playback\",\"value\":\"abcdefghijk\"}]}",
                1
        );
        CatalogEpisode ep2 = new CatalogEpisode(
                "ep_kid_2", "ser_kid", null, 2, "Tập 2", null, null, null, "regular", null,
                300_000L, null, null, null, null, null, null, null, null, null, null, null, null,
                READY_JSON,
                "{\"id\":\"ep_kid_2\",\"external_ids\":[{\"namespace\":\"youtube_playback\",\"value\":\"lmnopqrstuv\"}]}",
                1
        );
        CatalogEpisode comedyEp1 = new CatalogEpisode(
                "mov_comedy", "ser_comedy", null, 1, "Hài Tập 1", null, null, null, "regular", null,
                300_000L, null, null, null, null, null, null, null, null, null, null, null, null,
                READY_JSON,
                "{\"id\":\"mov_comedy\",\"external_ids\":[{\"namespace\":\"youtube_playback\",\"value\":\"comedy11111\"}]}",
                1
        );
        CatalogEpisode comedyEp2 = new CatalogEpisode(
                "mov_comedy_2", "ser_comedy", null, 2, "Hài Tập 2", null, null, null, "regular", null,
                300_000L, null, null, null, null, null, null, null, null, null, null, null, null,
                READY_JSON,
                "{\"id\":\"mov_comedy_2\",\"external_ids\":[{\"namespace\":\"youtube_playback\",\"value\":\"comedy22222\"}]}",
                1
        );
        CatalogEpisode advEp1 = new CatalogEpisode(
                "mov_adventure", "ser_adventure", null, 1, "Phiêu Lưu 1", null, null, null, "regular", null,
                300_000L, null, null, null, null, null, null, null, null, null, null, null, null,
                READY_JSON,
                "{\"id\":\"mov_adventure\",\"external_ids\":[{\"namespace\":\"youtube_playback\",\"value\":\"advent11111\"}]}",
                1
        );
        CatalogEpisode advEp2 = new CatalogEpisode(
                "mov_adventure_2", "ser_adventure", null, 2, "Phiêu Lưu 2", null, null, null, "regular", null,
                300_000L, null, null, null, null, null, null, null, null, null, null, null, null,
                READY_JSON,
                "{\"id\":\"mov_adventure_2\",\"external_ids\":[{\"namespace\":\"youtube_playback\",\"value\":\"advent22222\"}]}",
                1
        );

        episodeRepository.saveAllAndFlush(List.of(ep1, ep2, comedyEp1, comedyEp2, advEp1, advEp2));
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
        return extractToken(result);
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
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("id").asLong();
    }

    private String switchToChild(String token, long profileId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/profiles/" + profileId + "/switch")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return extractToken(result);
    }

    private String extractToken(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    private String json(Object payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
