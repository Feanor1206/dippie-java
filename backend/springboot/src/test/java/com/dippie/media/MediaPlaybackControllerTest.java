package com.dippie.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogEpisodeAsset;
import com.dippie.catalog.domain.CatalogEpisodeRepository;
import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.catalog.domain.CatalogMediaAssetRepository;
import com.dippie.catalog.domain.CatalogSeason;
import com.dippie.catalog.domain.CatalogSeasonRepository;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.domain.CatalogSeriesRepository;
import com.dippie.movie.Movie;
import com.dippie.movie.MovieRepository;
import com.dippie.security.JwtService;
import com.dippie.security.Role;
import com.dippie.user.User;
import com.dippie.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Bài test tích hợp cho toàn bộ luồng cấp vé phát, phân quyền xuất bản danh mục và phát video HTTP Range qua MockMvc.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MediaPlaybackControllerTest {

    private static final String READY = "{\"status\":\"ready\"}";
    private static final String DRAFT = "{\"status\":\"draft\"}";

    @TempDir
    static Path tempDir;

    static Path mediaRoot;
    static Path cacheRoot;
    static Path driveRoot;

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final MovieRepository movieRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final CatalogMediaAssetRepository catalogMediaAssetRepository;
    private final CatalogSeriesRepository catalogSeriesRepository;
    private final CatalogSeasonRepository catalogSeasonRepository;
    private final CatalogEpisodeRepository catalogEpisodeRepository;
    private final EntityManager entityManager;
    private final TransactionTemplate writes;
    private final JwtService jwtService;

    private String userAuth;
    private String adminAuth;

    MediaPlaybackControllerTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            MovieRepository movieRepository,
            MediaAssetRepository mediaAssetRepository,
            CatalogMediaAssetRepository catalogMediaAssetRepository,
            CatalogSeriesRepository catalogSeriesRepository,
            CatalogSeasonRepository catalogSeasonRepository,
            CatalogEpisodeRepository catalogEpisodeRepository,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager,
            JwtService jwtService
    ) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.movieRepository = movieRepository;
        this.mediaAssetRepository = mediaAssetRepository;
        this.catalogMediaAssetRepository = catalogMediaAssetRepository;
        this.catalogSeriesRepository = catalogSeriesRepository;
        this.catalogSeasonRepository = catalogSeasonRepository;
        this.catalogEpisodeRepository = catalogEpisodeRepository;
        this.entityManager = entityManager;
        this.writes = new TransactionTemplate(transactionManager);
        this.jwtService = jwtService;
    }

    @BeforeAll
    static void initDirectories() throws IOException {
        mediaRoot = Files.createDirectories(tempDir.resolve("media"));
        cacheRoot = Files.createDirectories(tempDir.resolve("cache"));
        driveRoot = Files.createDirectories(tempDir.resolve("drive"));
    }

    @DynamicPropertySource
    static void configureMediaPaths(DynamicPropertyRegistry registry) {
        registry.add("dippie.media.root", () -> mediaRoot.toString());
        registry.add("dippie.media.cache-root", () -> cacheRoot.toString());
        registry.add("dippie.media.drive-mount", () -> driveRoot.toString());
    }

    @BeforeEach
    void setUp() throws IOException {
        writes.executeWithoutResult(status -> entityManager.createQuery("delete from CatalogEpisodeAsset").executeUpdate());
        mediaAssetRepository.deleteAll();
        catalogMediaAssetRepository.deleteAll();
        catalogEpisodeRepository.deleteAll();
        catalogSeasonRepository.deleteAll();
        catalogSeriesRepository.deleteAll();
        movieRepository.deleteAll();
        userRepository.deleteAll();

        cleanDirectory(mediaRoot);
        cleanDirectory(cacheRoot);
        cleanDirectory(driveRoot);

        userRepository.saveAndFlush(new User(
                "viewer", "viewer@example.com", "pw", "Standard User",
                Role.ROLE_USER, LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        userRepository.saveAndFlush(new User(
                "admin", "admin@example.com", "pw", "System Administrator",
                Role.ROLE_ADMIN, LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        userAuth = "Bearer " + jwtService.issueLoginToken("viewer");
        adminAuth = "Bearer " + jwtService.issueLoginToken("admin");

        movieRepository.saveAndFlush(new Movie(
                "the-last-orbit", "The Last Orbit", 2026, 8.7, "2h 14m", "Animation,Sci-Fi",
                "Description", null, null, "/videos/the-last-orbit.mp4"
        ));
        savePublishedCatalogHierarchy("ser_orbit", "sea_orbit_1", "ep_orbit_1");
    }

    @Test
    void grantsPublicAssetAnonymouslyAndRejectsGrantAsLoginToken() throws Exception {
        byte[] content = "hello-media-world".getBytes(StandardCharsets.UTF_8);
        writeFile(mediaRoot.resolve("synthetic/feature_test.mp4"), content);
        savePublishedCatalogAsset("ast-orbit-feat", "ep_orbit_1", "local", "synthetic/feature_test.mp4",
                content.length, true, true, "public");

        MvcResult grantResult = mockMvc.perform(post("/api/media/grant/ast-orbit-feat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetId").value("ast-orbit-feat"))
                .andExpect(jsonPath("$.grant").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(300))
                .andExpect(jsonPath("$.expiresInSeconds").value(300))
                .andReturn();

        JsonNode grantBody = objectMapper.readTree(grantResult.getResponse().getContentAsString());
        String grantToken = grantBody.path("grant").asText();
        assertThat(grantBody.path("token").asText()).isEqualTo(grantToken);
        assertThat(grantBody.path("streamUrl").asText())
                .isEqualTo("/api/media/stream/ast-orbit-feat?grant=" + grantToken);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + grantToken))
                .andExpect(status().isUnauthorized());

        MvcResult tokenStream = performSyncGet("/api/media/stream/ast-orbit-feat?token=" + grantToken, null, null);
        assertThat(tokenStream.getResponse().getStatus()).isEqualTo(200);
        assertThat(tokenStream.getResponse().getContentAsByteArray()).isEqualTo(content);
    }

    @Test
    void rejectsNonCatalogAndUnpublishedSeriesSeasonOrEpisodeAssets() throws Exception {
        byte[] payload = "secret-episode-video".getBytes(StandardCharsets.UTF_8);
        writeFile(mediaRoot.resolve("synthetic/noncat.mp4"), payload);
        writeFile(mediaRoot.resolve("synthetic/draft_ep.mp4"), payload);
        writeFile(mediaRoot.resolve("synthetic/draft_sea.mp4"), payload);
        writeFile(mediaRoot.resolve("synthetic/draft_ser.mp4"), payload);

        // Bản ghi chỉ có trong media_assets mà chưa vào catalog_media_assets thì mọi vai trò đều ăn 404.
        saveRawMediaAsset("ast-noncat", "local", "synthetic/noncat.mp4", payload.length, true, true);
        mockMvc.perform(post("/api/media/grant/ast-noncat")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/media/grant/ast-noncat").header(HttpHeaders.AUTHORIZATION, userAuth))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/media/grant/ast-noncat").header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/media/stream/ast-noncat")).andExpect(status().isNotFound());

        // Tập đang ở trạng thái draft bị chặn với khách và user thường, chỉ admin mới xin vé xem trước được.
        catalogEpisodeRepository.saveAndFlush(new CatalogEpisode(
                "ep_draft", "ser_orbit", "sea_orbit_1", 2, "Tập nháp", null, null, null,
                "regular", null, 60_000L, null, null, null, null, null, null, null, null, null,
                null, null, null, DRAFT, null, 1
        ));
        savePublishedCatalogAsset("ast-draft-ep", "ep_draft", "local", "synthetic/draft_ep.mp4",
                payload.length, true, true, "public");
        mockMvc.perform(post("/api/media/grant/ast-draft-ep")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/media/grant/ast-draft-ep").header(HttpHeaders.AUTHORIZATION, userAuth))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/media/stream/ast-draft-ep")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/media/grant/ast-draft-ep").header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk());

        // Mùa còn draft thì mọi tập bên trong cũng bị ẩn dù bản thân tập đã đánh dấu ready.
        catalogSeasonRepository.saveAndFlush(new CatalogSeason(
                "sea_draft", "ser_orbit", 2, "Mùa nháp", null, "regular", null, null, null, null, DRAFT, null, 1
        ));
        catalogEpisodeRepository.saveAndFlush(new CatalogEpisode(
                "ep_in_draft_season", "ser_orbit", "sea_draft", 1, "Tập trong mùa nháp", null, null, null,
                "regular", null, 60_000L, null, null, null, null, null, null, null, null, null,
                null, null, null, READY, null, 1
        ));
        savePublishedCatalogAsset("ast-draft-sea", "ep_in_draft_season", "local", "synthetic/draft_sea.mp4",
                payload.length, true, true, "public");
        mockMvc.perform(post("/api/media/grant/ast-draft-sea")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/media/stream/ast-draft-sea").header(HttpHeaders.AUTHORIZATION, userAuth))
                .andExpect(status().isNotFound());

        // Cả bộ phim còn draft thì toàn bộ video con cũng bị ẩn theo.
        catalogSeriesRepository.saveAndFlush(new CatalogSeries(
                "ser_draft", "Bộ nháp", null, null, null, null, null, null, null, null, null,
                2026, null, null, null, null, null, null, null, null, null, DRAFT, null, 1
        ));
        catalogEpisodeRepository.saveAndFlush(new CatalogEpisode(
                "ep_in_draft_series", "ser_draft", null, 1, "Tập trong bộ nháp", null, null, null,
                "regular", null, 60_000L, null, null, null, null, null, null, null, null, null,
                null, null, null, READY, null, 1
        ));
        savePublishedCatalogAsset("ast-draft-ser", "ep_in_draft_series", "local", "synthetic/draft_ser.mp4",
                payload.length, true, true, "public");
        mockMvc.perform(post("/api/media/grant/ast-draft-ser")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/media/stream/ast-draft-ser")).andExpect(status().isNotFound());
    }

    @Test
    void enforcesPrivateAssetGrantAndRejectsCrossAssetGrant() throws Exception {
        byte[] payload = "0123456789abcdefghij".getBytes(StandardCharsets.UTF_8);
        writeFile(mediaRoot.resolve("synthetic/private.mp4"), payload);
        writeFile(mediaRoot.resolve("synthetic/other.mp4"), payload);

        savePublishedCatalogAsset("ast-private", "ep_orbit_1", "local", "synthetic/private.mp4",
                payload.length, false, true, "private");
        savePublishedCatalogAsset("ast-other", "ep_orbit_1", "local", "synthetic/other.mp4",
                payload.length, false, true, "private");

        mockMvc.perform(post("/api/media/grant/ast-private"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy video: ast-private"));

        mockMvc.perform(post("/api/media/grant/ast-private").header(HttpHeaders.AUTHORIZATION, userAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy video: ast-private"));

        mockMvc.perform(get("/api/media/stream/ast-private"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy video: ast-private"));

        mockMvc.perform(get("/api/media/stream/ast-private").param("grant", "invalid-token"))
                .andExpect(status().isNotFound());

        MvcResult adminGrant = mockMvc.perform(
                        post("/api/media/grant/ast-private").header(HttpHeaders.AUTHORIZATION, adminAuth)
                )
                .andExpect(status().isOk())
                .andReturn();
        JsonNode grantBody = objectMapper.readTree(adminGrant.getResponse().getContentAsString());
        String streamUrl = grantBody.path("streamUrl").asText();
        String grantToken = grantBody.path("grant").asText();

        MvcResult streamed = performSyncGet(streamUrl, "bytes=0-9", null);
        assertThat(streamed.getResponse().getStatus()).isEqualTo(206);
        assertThat(streamed.getResponse().getContentAsByteArray()).isEqualTo(Arrays.copyOfRange(payload, 0, 10));
        assertThat(streamed.getResponse().getHeader(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 0-9/20");

        mockMvc.perform(get("/api/media/stream/ast-other").param("grant", grantToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy video: ast-other"));

        // Kiểm tra payload JWT của vé phát không lộ username trong sub, và mất hiệu lực ngay khi tokenVersion tăng lên.
        String payloadJson = new String(
                java.util.Base64.getUrlDecoder().decode(grantToken.split("\\.")[1]),
                StandardCharsets.UTF_8
        );
        JsonNode claims = objectMapper.readTree(payloadJson);
        assertThat(claims.path("sub").asText()).doesNotContain("admin");
        assertThat(claims.path("uid").isIntegralNumber()).isTrue();

        User adminUser = userRepository.findByUsername("admin").orElseThrow();
        adminUser.incrementTokenVersion(LocalDateTime.of(2026, 1, 2, 0, 0));
        userRepository.saveAndFlush(adminUser);

        mockMvc.perform(get(streamUrl))
                .andExpect(status().isNotFound());
    }

    @Test
    void streamsLargePayloadAcrossChunksSupportsRangesHead416AndEmptyFile() throws Exception {
        byte[] payload = new byte[256 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }
        Path mediaFile = mediaRoot.resolve("synthetic/feature_test.mp4");
        writeFile(mediaFile, payload);
        savePublishedCatalogAsset("ast-orbit-feat", "ep_orbit_1", "local", "synthetic/feature_test.mp4",
                payload.length, true, true, "public");

        String url = "/api/media/stream/ast-orbit-feat";

        MvcResult full = performSyncGet(url, null, null);
        assertThat(full.getResponse().getStatus()).isEqualTo(200);
        assertThat(full.getResponse().getHeader(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(full.getResponse().getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo(String.valueOf(payload.length));
        assertThat(full.getResponse().getContentType()).startsWith("video/mp4");
        assertThat(full.getResponse().getContentAsByteArray()).isEqualTo(payload);

        assertPartialRange(url, "bytes=0-9", payload, 0, 9);
        assertPartialRange(url, "bytes=10-", payload, 10, payload.length - 1);
        assertPartialRange(url, "bytes=-5", payload, payload.length - 5, payload.length - 1);
        assertPartialRange(url, "bytes=10-29", payload, 10, 29);
        assertPartialRange(
                url,
                "bytes=" + (payload.length - 10) + "-" + (payload.length + 100),
                payload,
                payload.length - 10,
                payload.length - 1
        );

        for (String invalidRange : new String[]{
                "bytes=" + payload.length + "-",
                "bytes=20-10",
                "bytes=-0",
                "bytes=bad"
        }) {
            mockMvc.perform(get(url).header(HttpHeaders.RANGE, invalidRange))
                    .andExpect(status().isRequestedRangeNotSatisfiable())
                    .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */" + payload.length))
                    .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEmpty());
        }

        mockMvc.perform(head(url).header(HttpHeaders.RANGE, "bytes=0-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(payload.length)))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEmpty());

        writeFile(mediaFile, new byte[0]);
        MvcResult emptyGet = performSyncGet(url, null, null);
        assertThat(emptyGet.getResponse().getStatus()).isEqualTo(200);
        assertThat(emptyGet.getResponse().getContentAsByteArray()).isEmpty();
        assertThat(emptyGet.getResponse().getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo("0");

        mockMvc.perform(head(url))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "0"));

        mockMvc.perform(get(url).header(HttpHeaders.RANGE, "bytes=-1"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */0"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEmpty());
    }

    @Test
    void driveMirrorPrefersCompleteCacheCopyAndFallsBackToMount() throws Exception {
        String relativePath = "VideoDieppie/feature.mp4";
        Path mirror = cacheRoot.resolve(relativePath);
        Path drive = driveRoot.resolve(relativePath);
        byte[] mirrorBytes = "mirror-video".getBytes(StandardCharsets.UTF_8);
        byte[] driveBytes = "drive--video".getBytes(StandardCharsets.UTF_8);
        writeFile(mirror, mirrorBytes);
        writeFile(drive, driveBytes);

        savePublishedCatalogAsset("ast-drive", "ep_orbit_1", "drive", relativePath,
                mirrorBytes.length, true, true, "public");
        String url = "/api/media/stream/ast-drive";

        MvcResult rangeFromMirror = performSyncGet(url, "bytes=1-5", null);
        assertThat(rangeFromMirror.getResponse().getStatus()).isEqualTo(206);
        assertThat(rangeFromMirror.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEqualTo("irror");
        assertThat(rangeFromMirror.getResponse().getHeader(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 1-5/12");

        writeFile(mirror, "short".getBytes(StandardCharsets.UTF_8));
        assertThat(performSyncGet(url, null, null).getResponse().getContentAsByteArray()).isEqualTo(driveBytes);

        writeFile(mirror, "mirror-video-extra".getBytes(StandardCharsets.UTF_8));
        assertThat(performSyncGet(url, null, null).getResponse().getContentAsByteArray()).isEqualTo(driveBytes);

        writeFile(mirror, mirrorBytes);
        mediaAssetRepository.deleteById("ast-drive");
        saveRawMediaAsset("ast-drive", "drive", relativePath, 0, true, true);
        assertThat(performSyncGet(url, null, null).getResponse().getContentAsByteArray()).isEqualTo(driveBytes);

        mediaAssetRepository.deleteById("ast-drive");
        saveRawMediaAsset("ast-drive", "drive", relativePath, mirrorBytes.length, false, true);
        mockMvc.perform(get(url))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy video: ast-drive"));

        mediaAssetRepository.deleteById("ast-drive");
        saveRawMediaAsset("ast-drive", "drive", relativePath, mirrorBytes.length, true, true);
        Files.delete(mirror);
        assertThat(performSyncGet(url, null, null).getResponse().getContentAsByteArray()).isEqualTo(driveBytes);
    }

    @Test
    void returns404WhenVideoFileIsMissingOnDisk() throws Exception {
        savePublishedCatalogAsset("ast-missing-file", "ep_orbit_1", "local", "synthetic/does-not-exist.mp4",
                1024, true, true, "public");

        mockMvc.perform(get("/api/media/stream/ast-missing-file"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy video để phát."));
    }

    private void savePublishedCatalogHierarchy(String seriesId, String seasonId, String episodeId) {
        catalogSeriesRepository.saveAndFlush(new CatalogSeries(
                seriesId, "The Last Orbit", null, null, null, null, null, null, null, null, null,
                2026, null, null, null, null, null, null, null, null, null, READY, null, 1
        ));
        catalogSeasonRepository.saveAndFlush(new CatalogSeason(
                seasonId, seriesId, 1, "Mùa 1", null, "regular", null, null, null, null, READY, null, 1
        ));
        catalogEpisodeRepository.saveAndFlush(new CatalogEpisode(
                episodeId, seriesId, seasonId, 1, "Tập 1", null, null, null,
                "regular", null, 60_000L, null, null, null, null, null, null, null, null, null,
                null, null, null, READY, null, 1
        ));
    }

    private void savePublishedCatalogAsset(
            String id,
            String episodeId,
            String provider,
            String relativePath,
            long byteLength,
            boolean isPublic,
            boolean isAvailable,
            String accessPolicy
    ) {
        saveRawMediaAsset(id, provider, relativePath, byteLength, isPublic, isAvailable);
        catalogMediaAssetRepository.saveAndFlush(new CatalogMediaAsset(
                id, "video", "feature", null, null, null,
                provider, provider, relativePath, null, null, byteLength, null, 10_000L,
                null, null, null, accessPolicy, true, READY, null, 1
        ));
        writes.executeWithoutResult(status -> entityManager.persist(new CatalogEpisodeAsset(episodeId, id)));
    }

    private void assertPartialRange(String url, String rangeHeader, byte[] fullPayload, int start, int end)
            throws Exception {
        MvcResult result = performSyncGet(url, rangeHeader, null);
        assertThat(result.getResponse().getStatus()).isEqualTo(206);
        assertThat(result.getResponse().getHeader(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_RANGE))
                .isEqualTo("bytes " + start + "-" + end + "/" + fullPayload.length);
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_LENGTH))
                .isEqualTo(String.valueOf(end - start + 1));
        assertThat(result.getResponse().getContentAsByteArray())
                .isEqualTo(Arrays.copyOfRange(fullPayload, start, end + 1));
    }

    private MvcResult performSyncGet(String url, String rangeHeader, String authHeader) throws Exception {
        var requestBuilder = get(url);
        if (rangeHeader != null) {
            requestBuilder.header(HttpHeaders.RANGE, rangeHeader);
        }
        if (authHeader != null) {
            requestBuilder.header(HttpHeaders.AUTHORIZATION, authHeader);
        }
        // Khẳng định endpoint stream chạy đồng bộ hoàn toàn, không bật async dispatch.
        return mockMvc.perform(requestBuilder)
                .andExpect(request().asyncNotStarted())
                .andReturn();
    }

    private void saveRawMediaAsset(
            String id,
            String provider,
            String relativePath,
            long byteLength,
            boolean isPublic,
            boolean isAvailable
    ) {
        mediaAssetRepository.saveAndFlush(new MediaAsset(
                id,
                "the-last-orbit",
                "feature",
                provider,
                null,
                relativePath,
                "v-" + id,
                "video/mp4",
                byteLength,
                10.0,
                isPublic,
                isAvailable
        ));
    }

    private static void writeFile(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
    }

    private static void cleanDirectory(Path directory) throws IOException {
        try (var stream = Files.walk(directory)) {
            stream.filter(path -> !path.equals(directory))
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }
}
