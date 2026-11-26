package com.dippie.controller.catalog;

import com.dippie.model.catalog.CatalogImportedEvent;
import com.dippie.model.user.Role;
import com.dippie.model.user.User;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.JwtService;
import com.dippie.service.catalog.CatalogVisibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// test trọn luồng HTTP catalog: validate, import, phân quyền, ẩn hiện nháp, export với lưu tiến độ
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Việt để ý chỗ này cần DB với chỉ mục trắng tinh lúc đầu, tách context riêng kẻo dính rác test trước nha
@TestPropertySource(properties = "dippie.test.context=CatalogHttpTest")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@RecordApplicationEvents
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CatalogHttpTest {

    private static final String ADMIN = "/api/admin/catalog/";
    private static final String CATALOG = "/api/catalog/";

    @TempDir
    static Path mediaRoot;

    private final MockMvc mvc;
    private final ObjectMapper mapper;
    private final UserRepository users;
    private final JwtService jwt;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final JdbcTemplate database;
    private final CatalogVisibility visibility;
    private final EntityManagerFactory entityManagerFactory;

    CatalogHttpTest(MockMvc mvc, ObjectMapper mapper, UserRepository users, JwtService jwt,
            PasswordEncoder passwords, Clock clock, JdbcTemplate database, CatalogVisibility visibility,
            EntityManagerFactory entityManagerFactory) {
        this.mvc = mvc;
        this.mapper = mapper;
        this.users = users;
        this.jwt = jwt;
        this.passwords = passwords;
        this.clock = clock;
        this.database = database;
        this.visibility = visibility;
        this.entityManagerFactory = entityManagerFactory;
    }

    @DynamicPropertySource
    static void configureMediaRoot(DynamicPropertyRegistry registry) {
        registry.add("dippie.media.root", () -> mediaRoot.toString());
    }

    @Test
    void episodePlaybackAvailabilityUsesTheCurrentFilesAndYoutubeNamespaces() throws Exception {
        String admin = token("catalog-w9-availability", Role.ROLE_ADMIN);
        submit("import", admin, """
                {
                  "schema_version":"dippie.catalog.v1",
                  "package":{"id":"pkg-playback","revision":1},
                  "series":[{"id":"playback-series","title":"Playback"}],
                  "episodes":[
                    {"id":"playback-jadoo","series_id":"playback-series","number":1,"title":"Jadoo","duration_ms":100000,
                     "external_ids":[{"namespace":"youtube_video_id","value":"invalid"},
                                     {"namespace":"youtube_video_id","value":"2ykw6ZxTb8o"}]},
                    {"id":"playback-larva","series_id":"playback-series","number":2,"title":"Larva",
                     "external_ids":[{"namespace":"youtube_video_id","value":"other_00001"},
                                     {"namespace":"youtube_playback","value":"9aFcot7_7Q4"}]},
                    {"id":"playback-invalid","series_id":"playback-series","number":3,"title":"Invalid ID",
                     "external_ids":[{"namespace":"youtube_video_id","value":"invalid"}]}],
                  "media_assets":[
                    {"id":"playback-missing","storage":{"provider":"local","relative_path":"missing.mp4"},
                     "coverage":[{"episode_id":"playback-jadoo","scope":"full_episode"}]},
                    {"id":"playback-present","storage":{"provider":"local","relative_path":"present.mp4"},
                     "coverage":[{"episode_id":"playback-jadoo","scope":"full_episode"}]}]
                }
                """).andExpect(status().isOk());
        Path file = Files.writeString(mediaRoot.resolve("present.mp4"), "test video");

        mvc.perform(get(CATALOG + "episodes/playback-jadoo")).andExpect(status().isOk())
                .andExpect(jsonPath("$.youtube").value(nullValue()))
                .andExpect(jsonPath("$.youtubeFallback.videoId").value("2ykw6ZxTb8o"))
                .andExpect(jsonPath("$.youtubeFallback.canEmbed").value(true))
                .andExpect(jsonPath("$.mediaAssets[*].id", contains("playback-missing", "playback-present")))
                .andExpect(jsonPath("$.mediaAssets[0].available").value(false))
                .andExpect(jsonPath("$.mediaAssets[1].available").value(true));
        mvc.perform(get("/api/movies/playback-jadoo/media-descriptor")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasFeature").value(true))
                .andExpect(jsonPath("$.data.feature.assetId").value("playback-present"));
        mvc.perform(post("/api/media/grant/playback-present")).andExpect(status().isOk())
                .andExpect(jsonPath("$.streamUrl").isNotEmpty());

        String member = token("fallback-progress", Role.ROLE_USER);
        String progressPath = "/api/users/watch-progress/playback-jadoo";
        String body = progressBody("youtube:2ykw6ZxTb8o", 60_000, -1);
        // có file local hay Drive sẵn trên đĩa rồi nhưng xem YouTube dự phòng vẫn phải lưu tiến độ được nha
        mvc.perform(auth(put(progressPath), member).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durationMs").value(100_000))
                .andExpect(jsonPath("$.data.assetId").value("youtube:2ykw6ZxTb8o"))
                .andExpect(jsonPath("$.data.version").value(0));
        Files.delete(file);
        mvc.perform(get("/api/movies/playback-jadoo/media-descriptor")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasFeature").value(false))
                .andExpect(jsonPath("$.data.feature").value(nullValue()));
        mvc.perform(post("/api/media/grant/playback-present")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy video để phát."));
        mvc.perform(auth(put(progressPath), member).contentType(MediaType.APPLICATION_JSON)
                        .content(progressBody("youtube:2ykw6ZxTb8o", 70_000, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.durationMs").value(100_000))
                .andExpect(jsonPath("$.data.assetId").value("youtube:2ykw6ZxTb8o"))
                .andExpect(jsonPath("$.data.version").value(1));

        database.update("delete from media_assets where id = ?", "playback-present");
        mvc.perform(get(CATALOG + "episodes/playback-jadoo")).andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaAssets[1].available").value(false));

        mvc.perform(get(CATALOG + "episodes/playback-larva")).andExpect(status().isOk())
                .andExpect(jsonPath("$.youtube.videoId").value("9aFcot7_7Q4"))
                .andExpect(jsonPath("$.youtube.canEmbed").value(true))
                .andExpect(jsonPath("$.youtubeFallback").value(nullValue()));
        mvc.perform(get(CATALOG + "episodes/playback-invalid")).andExpect(status().isOk())
                .andExpect(jsonPath("$.youtube").value(nullValue()))
                .andExpect(jsonPath("$.youtubeFallback").value(nullValue()));
    }

    @Test
    void importsJadooAndKeepsTheSameSnapshotForAnUnchangedPackage(ApplicationEvents events) throws Exception {
        String admin = token("catalog-jadoo", Role.ROLE_ADMIN);
        String fixture = jadooFixture();
        assertThat(visibility.isSeriesVisible("ser_jadoo")).isFalse();

        submit("validate", admin, fixture).andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.packageId").value("pkg_dippie_series_jadoo"))
                .andExpect(jsonPath("$.revision").value(5))
                .andExpect(jsonPath("$.toCreate").value(715))
                .andExpect(jsonPath("$.toUpdate").value(0))
                .andExpect(jsonPath("$.unchanged").value(0))
                .andExpect(jsonPath("$.conflicts").value(0));

        JsonNode imported = body(submit("import", admin, fixture).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.indexStatus").value("READY"))
                .andExpect(jsonPath("$.counts.episodes").value(99))
                .andExpect(jsonPath("$.counts.mediaAssets").value(198)));
        String snapshot = imported.path("importId").asText();
        assertThat(visibility.isSeriesVisible("ser_jadoo")).isTrue();
        String importedAtText = imported.path("importedAt").asText();
        assertThat(importedAtText).endsWith("Z");
        assertThat(events.stream(CatalogImportedEvent.class)).containsExactly(new CatalogImportedEvent(
                "pkg_dippie_series_jadoo", 5, imported.path("snapshotVersion").asLong(), snapshot,
                "catalog-jadoo", LocalDateTime.ofInstant(Instant.parse(importedAtText), ZoneOffset.UTC)));
        verifyNextEpisodeAfterJadooImport();

        mvc.perform(auth(get(ADMIN + "history"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("pkg_dippie_series_jadoo"))
                .andExpect(jsonPath("$[0].status").value("COMMITTED"));
        JsonNode snapshots = body(mvc.perform(auth(get(ADMIN + "snapshots"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(snapshot))
                .andExpect(jsonPath("$[0].status").value("READY"))
                .andExpect(jsonPath("$[0].totalItems").value(714)));
        mvc.perform(auth(get(ADMIN + "export"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.schema_version").value("dippie.catalog.v1"))
                .andExpect(jsonPath("$.series[*].id", hasItem("ser_jadoo")))
                .andExpect(jsonPath("$.media_assets[*].id", hasItem("asset_jadoo_s01_a0001")));
        mvc.perform(auth(get(ADMIN + "export").param("includePrivate", "false"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media_assets[*].id", not(hasItem("asset_jadoo_s01_a0001"))));
        assertThat(database.queryForList("select genre_id from catalog_series_genres where series_id = 'ser_jadoo'", String.class))
                .hasSize(5);
        assertThat(database.queryForList("select character_id from catalog_series_characters where series_id = 'ser_jadoo'", String.class))
                .hasSize(9);
        assertThat(database.queryForList("select character_id from catalog_character_series where series_id = 'ser_jadoo'", String.class))
                .hasSize(9);
        assertThat(database.queryForList("select genre_id from catalog_episode_genres where episode_id = 'ep_jadoo_s01_0001'", String.class))
                .isNotEmpty();
        assertThat(database.queryForList("select character_id from catalog_episode_characters where episode_id = 'ep_jadoo_s01_0001'", String.class))
                .isNotEmpty();
        assertThat(database.queryForList("select genre_name from movie_genres where movie_id = 'ser_jadoo'", String.class))
                .containsExactlyInAnyOrder("animation", "comedy", "family", "school", "slice_of_life");
        assertThat(database.queryForObject("select mime_type from media_assets where id = 'asset_jadoo_s01_a0001'", String.class))
                .isEqualTo("audio/mpeg");

        submit("validate", admin, fixture).andExpect(status().isOk())
                .andExpect(jsonPath("$.unchanged").value(715));
        JsonNode repeated = body(submit("import", admin, fixture).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Gói danh mục này đã được nhập trước đó."))
                .andExpect(jsonPath("$.unchanged").doesNotHaveJsonPath()));
        assertThat(repeated.path("importId")).isEqualTo(imported.path("importId"));
        assertThat(repeated.path("importedAt")).isEqualTo(imported.path("importedAt"));
        assertThat(body(mvc.perform(auth(get(ADMIN + "snapshots"), admin)).andExpect(status().isOk())).size())
                .isEqualTo(snapshots.size());

        ObjectNode conflicting = (ObjectNode) mapper.readTree(fixture);
        ((ObjectNode) conflicting.path("series").get(0)).put("title", "Changed at the same revision");
        submit("validate", admin, conflicting.toString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.conflicts").value(2));
        submit("import", admin, conflicting.toString()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Gói danh mục xung đột với phiên bản đã lưu."));

        mvc.perform(auth(post(ADMIN + "snapshots/" + snapshot + "/retry"), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.retryCount").value(1));
        assertThat(events.stream(CatalogImportedEvent.class)).hasSize(1);
        submit("import", admin, "{\"package\":{\"id\":\"pkg_dippie_series_jadoo\",\"revision\":6}}")
                .andExpect(status().isOk());
        mvc.perform(auth(post(ADMIN + "snapshots/" + snapshot + "/retry"), admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chỉ được đồng bộ lại snapshot mới nhất."));
        mvc.perform(auth(post(ADMIN + "snapshots/unknown/retry"), admin)).andExpect(status().isBadRequest());
    }

    private void verifyNextEpisodeAfterJadooImport() throws Exception {
        String endpoint = "/api/users/next-episode";
        String member = token("jadoo-next-episode", Role.ROLE_USER);
        var jadoo = get(endpoint).param("seriesId", "ser_jadoo");
        mvc.perform(jadoo).andExpect(status().isUnauthorized());
        mvc.perform(auth(get(endpoint).param("seriesId", "unknown-series"), member))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy bộ phim với mã: unknown-series"));
        mvc.perform(auth(jadoo, member)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.seriesId").value("ser_jadoo"))
                .andExpect(jsonPath("$.data.episodeId").value("ep_jadoo_s01_0001"))
                .andExpect(jsonPath("$.data.episodeTitle").isNotEmpty())
                .andExpect(jsonPath("$.data.seasonNumber").value(1))
                .andExpect(jsonPath("$.data.episodeNumber").value(1))
                .andExpect(jsonPath("$.data.resumePositionMs").value(0))
                .andExpect(jsonPath("$.data.reason").value("NEXT_UNWATCHED"));

        var userId = users.findByUsername("jadoo-next-episode").orElseThrow().getId();
        database.update("insert into watch_progress (user_id, episode_id, asset_id, position_ms) values (?, ?, ?, ?)",
                userId, "ep_jadoo_s01_0002", "asset_jadoo_s01_v0002", 1_200);
        mvc.perform(auth(get(endpoint).param("seriesId", "ser_jadoo"), member)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.episodeId").value("ep_jadoo_s01_0002"))
                .andExpect(jsonPath("$.data.resumePositionMs").value(1_200))
                .andExpect(jsonPath("$.data.reason").value("RESUME"));

        database.update("delete from watch_progress where user_id = ?", userId);
        database.update("""
                insert into watch_progress (user_id, episode_id, asset_id, position_ms)
                select ?, episode.id, link.media_asset_id, episode.duration_ms
                from catalog_episodes episode
                join catalog_episode_assets link on link.episode_id = episode.id
                join catalog_media_assets asset on asset.id = link.media_asset_id and asset.role = 'feature'
                where episode.series_id = 'ser_jadoo'
                """, userId);
        mvc.perform(auth(get(endpoint).param("seriesId", "ser_jadoo"), member)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.message").value("Bạn đã xem hết bộ phim này."));
        database.update("delete from watch_progress where user_id = ?", userId);
    }

    @Test
    void authorizationAndInvalidInputUseTheirContractBodies() throws Exception {
        String admin = token("catalog-validate", Role.ROLE_ADMIN);
        String user = token("catalog-member", Role.ROLE_USER);
        ResultMatcher denied = jsonPath("$.message").value("Chỉ tài khoản quản trị mới dùng được mục này.");

        for (String endpoint : new String[]{"history", "snapshots", "export"}) {
            mvc.perform(auth(get(ADMIN + endpoint), user)).andExpect(status().isForbidden()).andExpect(denied);
        }
        mvc.perform(auth(post(ADMIN + "snapshots/unknown/retry"), user))
                .andExpect(status().isForbidden()).andExpect(denied);
        mvc.perform(auth(get(ADMIN + "export").param("includeDraft", "invalid"), user))
                .andExpect(status().isForbidden()).andExpect(denied);
        submit("validate", user, "{").andExpect(status().isForbidden()).andExpect(denied);
        submit("import", user, "{").andExpect(status().isForbidden()).andExpect(denied);
        mvc.perform(get(ADMIN + "history")).andExpect(status().isForbidden()).andExpect(denied);

        submit("validate", admin, "{ {} }").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.conflicts").value(0))
                .andExpect(jsonPath("$.errors[0].path").value("package"))
                .andExpect(jsonPath("$.errors[0].code").value("INVALID_FIELD"))
                .andExpect(jsonPath("$.errors[0].message").value("Dữ liệu gửi lên không đúng định dạng."));
        submit("import", admin, "{ {} }").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Dữ liệu gửi lên không đúng định dạng."));
    }

    @Test
    @SuppressWarnings("resource") // SessionFactory là của Spring, đóng là sập context test
    void catalogVisibilityCascadesAndSearchPreservesHitOrder() throws Exception {
        String admin = token("catalog-visibility", Role.ROLE_ADMIN);
        String member = token("catalog-playback", Role.ROLE_USER);
        submit("import", admin, """
                {
                    "package": {"id":"visibility"},
                    "sources": [{"id":"probe-source","kind":"official"},
                                {"id":"draft-probe-source","kind":"official","record":{"status":"draft"}}],
                    "series": [{"id":"visible-series","title":"Probe series"},
                               {"id":"draft-series","title":"Probe draft","record":{"status":"draft"}}],
                    "seasons": [{"id":"draft-season","series_id":"visible-series","number":1,"record":{"status":"draft"}}],
                    "episodes": [
                       {"id":"visible-episode","series_id":"visible-series","number":2,"title":"Probe episode",
                        "external_ids":[{"namespace":"youtube_watch","value":"watch_00001"}]},
                       {"id":"draft-episode","series_id":"visible-series","number":3,"title":"Probe draft","record":{"status":"draft"}},
                       {"id":"child-of-draft-season","series_id":"visible-series","season_id":"draft-season","number":1,"title":"Probe hidden"},
                       {"id":"child-of-draft-series","series_id":"draft-series","title":"Probe hidden"}],
                    "segments": [{"id":"visible-segment","episode_id":"visible-episode","title":"Probe segment",
                                  "episode_start_ms":20,"episode_end_ms":100}],
                    "media_assets": [
                       {"id":"asset-public","coverage":[{"episode_id":"visible-episode"}]},
                       {"id":"asset-private","access_policy":"private","coverage":[{"episode_id":"visible-episode"}]},
                       {"id":"asset-auth","access_policy":"authenticated","coverage":[{"episode_id":"visible-episode"}]},
                       {"id":"asset-disabled","enabled":false,"coverage":[{"episode_id":"visible-episode"}]},
                       {"id":"asset-draft","record":{"status":"draft"},"coverage":[{"episode_id":"visible-episode"}]}],
                    "text_tracks": [
                       {"id":"probe-track","kind":"subtitle","episode_id":"visible-episode","source_id":"probe-source"},
                       {"id":"draft-source-track","kind":"subtitle","episode_id":"visible-episode","source_id":"draft-probe-source"},
                       {"id":"draft-episode-track","kind":"subtitle","episode_id":"draft-episode","source_id":"probe-source"},
                       {"id":"private-asset-track","kind":"subtitle","media_asset_id":"asset-private"}],
                    "scenes": [
                       {"id":"scene-public","episode_id":"visible-episode","media_asset_id":"asset-public","summary":"Probe scene","start_ms":0,"end_ms":40},
                       {"id":"scene-private","episode_id":"visible-episode","media_asset_id":"asset-private","summary":"Probe hidden","start_ms":40,"end_ms":80}]
                }
                """).andExpect(status().isOk());

        mvc.perform(get(CATALOG + "series")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem("visible-series")))
                .andExpect(jsonPath("$[*].id", not(hasItem("draft-series"))));
        mvc.perform(auth(get(CATALOG + "series"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem("draft-series")));
        mvc.perform(get(CATALOG + "series/draft-series")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy bộ phim với mã: draft-series"));
        mvc.perform(auth(get("/api/users/next-episode").param("seriesId", "draft-series"), admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy bộ phim với mã: draft-series"));
        mvc.perform(auth(get("/api/users/next-episode").param("seriesId", "visible-series"), member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.episodeId").value("visible-episode"))
                .andExpect(jsonPath("$.data.reason").value("NEXT_UNWATCHED"));
        mvc.perform(get(CATALOG + "series/visible-series")).andExpect(status().isOk())
                .andExpect(jsonPath("$.seasons").isEmpty())
                .andExpect(jsonPath("$.episodes[*].id", contains("visible-episode")));
        mvc.perform(auth(get(CATALOG + "series/visible-series"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.episodes[*].id", contains("child-of-draft-season", "visible-episode", "draft-episode")));
        for (String id : new String[]{"child-of-draft-season", "child-of-draft-series", "draft-episode"}) {
            mvc.perform(get(CATALOG + "episodes/" + id)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Không tìm thấy tập phim với mã: " + id));
            mvc.perform(auth(get(CATALOG + "episodes/" + id), admin)).andExpect(status().isOk());
        }

        mvc.perform(get(CATALOG + "episodes/visible-episode")).andExpect(status().isOk())
                .andExpect(jsonPath("$.youtube.videoId").value("watch_00001"))
                .andExpect(jsonPath("$.youtube.canEmbed").value(false))
                .andExpect(jsonPath("$.mediaAssets[*].id", contains("asset-public")))
                .andExpect(jsonPath("$.scenes[*].id", contains("scene-public")));
        mvc.perform(auth(get(CATALOG + "episodes/visible-episode"), member)).andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaAssets[*].id", contains("asset-auth", "asset-public")));
        mvc.perform(auth(get(CATALOG + "episodes/visible-episode"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaAssets[*].id", contains("asset-auth", "asset-draft", "asset-private", "asset-public")))
                .andExpect(jsonPath("$.scenes[*].id", contains("scene-public", "scene-private")));

        Statistics queries = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean previouslyEnabled = queries.isStatisticsEnabled();
        queries.setStatisticsEnabled(true);
        long before = queries.getPrepareStatementCount();
        try {
            mvc.perform(get(CATALOG + "search").param("q", "  pRoBe ")).andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].type", contains("series", "episode", "segment", "scene")))
                    .andExpect(jsonPath("$[2].startMs").value(20))
                    .andExpect(jsonPath("$[3].title").value("Phân cảnh khớp truy vấn"));
            assertThat(queries.getPrepareStatementCount() - before).isEqualTo(1);
        } finally {
            queries.setStatisticsEnabled(previouslyEnabled);
        }
        mvc.perform(auth(get(CATALOG + "search").param("q", "probe hidden"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem("scene-private")))
                .andExpect(jsonPath("$[*].id", hasItem("child-of-draft-series")));
        mvc.perform(get(CATALOG + "search")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        assertThat(visibility.hiddenMovieIds()).contains("draft-series", "draft-episode", "child-of-draft-series",
                "child-of-draft-season").doesNotContain("visible-series", "visible-episode");

        mvc.perform(auth(get(ADMIN + "export").param("includeDraft", "f").param("includePrivate", "0"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series[*].id", not(hasItem("draft-series"))))
                .andExpect(jsonPath("$.episodes[*].id", not(hasItem("draft-episode"))))
                .andExpect(jsonPath("$.media_assets[*].id", not(hasItem("asset-private"))))
                .andExpect(jsonPath("$.text_tracks[*].id", contains("probe-track")));
        mvc.perform(auth(get(ADMIN + "export"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.text_tracks.length()").value(4));
    }

    @Test
    void rejectsWrongSeasonAndRollsBackAConstraintFailure() throws Exception {
        String admin = token("catalog-rollback", Role.ROLE_ADMIN);
        String wrongSeason = """
                {"package":{"id":"wrong-season"},"series":[{"id":"parent-a"},{"id":"parent-b"}],
                 "seasons":[{"id":"other-season","series_id":"parent-b"}],
                 "episodes":[{"id":"wrong-child","series_id":"parent-a","season_id":"other-season"}]}
                """;
        submit("validate", admin, wrongSeason).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].path").value("episodes[0].season_id"));
        submit("import", admin, wrongSeason).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mùa phim phải tồn tại và thuộc đúng bộ phim của tập này."));

        String missingAsset = """
                {"package":{"id":"rollback"},"series":[{"id":"rollback-series"}],
                 "episodes":[{"id":"rollback-episode","series_id":"rollback-series"}],
                 "text_tracks":[{"id":"invalid-track","kind":"subtitle","media_asset_id":"missing-asset"}]}
                """;
        submit("validate", admin, missingAsset).andExpect(status().isOk());
        submit("import", admin, missingAsset).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu hoặc liên kết trong gói danh mục không hợp lệ."));
        mvc.perform(auth(get(CATALOG + "series/rollback-series"), admin)).andExpect(status().isNotFound());
        mvc.perform(auth(get(ADMIN + "history"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem("rollback"))));
        assertThat(database.queryForObject("select count(*) from movies where id like 'rollback-%'", Long.class)).isZero();
    }

    @Test
    void serializesConflictingConcurrentImports() throws Exception {
        String admin = token("catalog-race", Role.ROLE_ADMIN);
        String base = "{\"package\":{\"id\":\"race\"},\"series\":[{\"id\":\"race-series\",\"title\":\"%s\"}]}";
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                start.await();
                return submit("import", admin, base.formatted("First")).andReturn().getResponse().getStatus();
            });
            var second = pool.submit(() -> {
                start.await();
                return submit("import", admin, base.formatted("Second")).andReturn().getResponse().getStatus();
            });
            start.countDown();
            assertThat(new int[]{first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(200, 409);
        }
    }

    @Test
    void updatesSegmentPositionsAndMovesAssetCoverage() throws Exception {
        String admin = token("catalog-move", Role.ROLE_ADMIN);
        String initial = """
                {"package":{"id":"move"},"series":[{"id":"move-series"},{"id":"another-move-series"}],
                 "seasons":[{"id":"move-season","series_id":"move-series"}],
                 "episodes":[{"id":"move-a","series_id":"move-series","season_id":"move-season"},
                             {"id":"move-b","series_id":"move-series"}],
                 "segments":[{"id":"move-first","episode_id":"move-a","position":1},
                             {"id":"move-second","episode_id":"move-a","position":2},
                             {"id":"move-legacy","episode_id":"move-a","position":3}],
                 "media_assets":[{"id":"move-asset","content_type":"audio","role":"dub",
                                  "storage":{"relative_path":"audio.aac"},
                                  "coverage":[{"episode_id":"move-a"},{"episode_id":"move-a"}]}]}
                """;
        submit("import", admin, initial).andExpect(status().isOk());
        assertThat(database.queryForObject("select mime_type from media_assets where id = 'move-asset'", String.class))
                .isEqualTo("audio/aac");
        database.update("update catalog_segments set position = -1 where id = 'move-legacy'");
        submit("validate", admin, """
                {"package":{"id":"move","revision":2},
                 "seasons":[{"id":"move-season","series_id":"another-move-series"}]}
                """).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].path").value("seasons[0].series_id"));

        String update = """
                {"package":{"id":"move","revision":2},
                 "segments":[{"id":"move-first","episode_id":"move-a","position":2},
                             {"id":"move-second","episode_id":"move-a","position":1}],
                 "media_assets":[{"id":"move-asset","coverage":[{"episode_id":"move-b"}]}]}
                """;
        submit("validate", admin, update).andExpect(status().isOk())
                .andExpect(jsonPath("$.toUpdate").value(4));
        submit("import", admin, update).andExpect(status().isOk());
        mvc.perform(get(CATALOG + "episodes/move-a")).andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaAssets").isEmpty())
                .andExpect(jsonPath("$.segments[*].id", contains("move-legacy", "move-second", "move-first")));
        mvc.perform(get(CATALOG + "episodes/move-b")).andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaAssets[*].id", contains("move-asset")));
        assertThat(database.queryForObject("select movie_id from media_assets where id = 'move-asset'", String.class))
                .isEqualTo("move-b");
    }

    @Test
    void reimportRepairsGenreLinksAndAppliesTheCatalogYearFallbacks() throws Exception {
        String admin = token("catalog-legacy-metadata", Role.ROLE_ADMIN);
        String fixture = """
                {"package":{"id":"legacy-metadata"},
                 "genres":[{"id":"metadata-family","labels":[{"language":"en","text":"Family test"},
                                                               {"language":"vi","text":"Gia đình thử nghiệm"}]}],
                 "series":[{"id":"metadata-series","title":"Metadata","release_year_start":2001,
                            "genre_ids":["metadata-family"]},
                           {"id":"metadata-undated","title":"Undated"}],
                 "seasons":[{"id":"metadata-season","series_id":"metadata-series","release_year_start":2003}],
                 "episodes":[{"id":"metadata-aired","series_id":"metadata-series","season_id":"metadata-season",
                              "release_date":"2005-01-01"},
                             {"id":"metadata-season-year","series_id":"metadata-series","season_id":"metadata-season"},
                             {"id":"metadata-series-year","series_id":"metadata-series"},
                             {"id":"metadata-unknown","series_id":"metadata-undated"}]}
                """;
        submit("import", admin, fixture).andExpect(status().isOk());
        database.update("update movies set genres = 'Animation', release_year = 2026 where id like 'metadata-%'");
        database.update("delete from movie_genres where movie_id like 'metadata-%' and genre_name <> 'animation'");
        submit("import", admin, fixture).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Gói danh mục này đã được nhập trước đó."));

        mvc.perform(get("/api/movies/browse").param("genre", "FAMILY TEST").param("sort", "year"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.items[*].year", contains(2005, 2003, 2001, 2001)))
                .andExpect(jsonPath("$.data.items[0].genres", hasItem("metadata-family")));
        mvc.perform(get("/api/movies/browse").param("genre", "METADATA-FAMILY").param("yearMin", "2003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/movies/genres")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'metadata-family')].label", contains("Gia đình thử nghiệm")))
                .andExpect(jsonPath("$[?(@.id == 'metadata-family')].count", contains(4)));
        mvc.perform(get("/api/movies/metadata-unknown")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.year").value(nullValue()));
        assertThat(database.queryForObject("select release_year from movies where id = 'metadata-unknown'", Integer.class))
                .isNull();

        submit("import", admin, """
                {"package":{"id":"legacy-metadata","revision":2},
                 "series":[{"id":"metadata-series","title":"Metadata","release_year_start":2002}]}
                """).andExpect(status().isOk());
        mvc.perform(get("/api/movies/metadata-series-year")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.year").value(2002))
                .andExpect(jsonPath("$.data.genres", contains("animation")));
        mvc.perform(get("/api/movies/genres")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'metadata-family')]").isEmpty());
    }

    @Test
    void youtubeProgressSupportsContinueWatchingAndAdvancesToTheNextEpisode() throws Exception {
        String admin = token("catalog-larva", Role.ROLE_ADMIN);
        String fixture = Files.readString(Path.of("../../data/catalog-by-series/catalog.larva.json"));
        submit("import", admin, fixture).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        mvc.perform(get("/api/movies/browse").param("animationOnly", "true").param("genre", "Family")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].genres", hasItem("family")));
        String member = token("larva-next-episode", Role.ROLE_USER);

        mvc.perform(auth(get("/api/users/next-episode").param("seriesId", "ser_larva"), member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.episodeId").value("ep_larva_s01_0001"))
                .andExpect(jsonPath("$.data.seasonNumber").value(1))
                .andExpect(jsonPath("$.data.episodeNumber").value(1))
                .andExpect(jsonPath("$.data.reason").value("NEXT_UNWATCHED"));

        String episode = "ep_larva_s01_0001";
        String path = "/api/users/watch-progress/" + episode;
        JsonNode detail = mapper.readTree(mvc.perform(get(CATALOG + "episodes/" + episode))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        String asset = "youtube:" + detail.path("youtube").path("videoId").asText();
        mvc.perform(auth(put(path), member).contentType(MediaType.APPLICATION_JSON)
                        .content(progressBody("youtube:wrong_id_01", 60_000, -1)))
                .andExpect(status().isNotFound());
        mvc.perform(auth(put(path), member).contentType(MediaType.APPLICATION_JSON)
                        .content(progressBody(asset, 60_000, -1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetId").value(asset))
                .andExpect(jsonPath("$.data.durationMs").value(139_000))
                .andExpect(jsonPath("$.data.completed").value(false));
        mvc.perform(auth(get(path), member)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetId").value(asset));
        mvc.perform(auth(get("/api/users/watch-progress"), member)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].assetId").value(asset));
        mvc.perform(auth(get("/api/users/next-episode").param("seriesId", "ser_larva"), member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.episodeId").value(episode))
                .andExpect(jsonPath("$.data.assetId").value(asset))
                .andExpect(jsonPath("$.data.resumePositionMs").value(60_000))
                .andExpect(jsonPath("$.data.reason").value("RESUME"));
        mvc.perform(auth(put(path), member).contentType(MediaType.APPLICATION_JSON)
                        .content(progressBody(asset, 139_000, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true));
        mvc.perform(auth(get("/api/users/next-episode").param("seriesId", "ser_larva"), member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.episodeId").value("ep_larva_s01_0002"))
                .andExpect(jsonPath("$.data.reason").value("NEXT_UNWATCHED"));
    }

    private String progressBody(String assetId, long positionMs, long version) {
        return mapper.createObjectNode().put("assetId", assetId)
                .put("positionMs", positionMs).put("expectedVersion", version).toString();
    }

    @Test
    void seriesCardsAndExistingEndpointsUseCatalogArtwork() throws Exception {
        String admin = token("catalog-cards", Role.ROLE_ADMIN);
        submit("import", admin, """
                {"package":{"id":"cards-fixture"},
                 "genres":[{"id":"cards-english","labels":[{"language":"en","text":"Family"}]},
                           {"id":"cards-vietnamese","labels":[{"language":"en","text":"Comedy"},
                                                              {"language":"vi","text":"Hài"}]}],
                 "series":[{"id":"cards-featured","title":"W6 Z","original_title":"Featured",
                            "synopsis":"Cards fixture","release_year_start":2011,
                            "genre_ids":["cards-vietnamese","cards-english"],
                            "artwork":[{"role":"poster","uri":"https://example.com/cards.jpg"}]},
                           {"id":"cards-empty","title":"W6 a"},
                           {"id":"cards-draft","title":"W6 A","record":{"status":"draft"}}],
                 "seasons":[{"id":"cards-first","series_id":"cards-featured","number":1},
                            {"id":"cards-second","series_id":"cards-featured","number":2},
                            {"id":"cards-vacant","series_id":"cards-featured","number":3},
                            {"id":"cards-hidden","series_id":"cards-featured","number":0,
                             "record":{"status":"draft"}}],
                 "episodes":[
                   {"id":"cards-later","series_id":"cards-featured","season_id":"cards-second","number":1,
                    "external_ids":[{"namespace":"youtube_playback","value":"later_00001"}]},
                   {"id":"cards-feature","title":"Cards featured episode","series_id":"cards-featured","season_id":"cards-first","number":2,
                    "external_ids":[{"namespace":"youtube_watch","value":"video_00001"}]},
                   {"id":"cards-no-youtube","series_id":"cards-featured","season_id":"cards-first","number":1,
                    "external_ids":[{"namespace":"youtube_playback","value":"invalid"}]},
                   {"id":"cards-hidden-episode","series_id":"cards-featured","season_id":"cards-hidden","number":1,
                    "external_ids":[{"namespace":"youtube_playback","value":"draft_00001"}]},
                   {"id":"cards-draft-episode","series_id":"cards-featured","season_id":"cards-first","number":0,
                    "record":{"status":"draft"},
                    "external_ids":[{"namespace":"youtube_playback","value":"draft_00002"}]}],
                 "media_assets":[
                   {"id":"cards-video","storage":{"relative_path":"cards.mp4"},
                    "coverage":[{"episode_id":"cards-feature","scope":"full_episode"}]},
                   {"id":"cards-fallback-video","storage":{"relative_path":"fallback.mp4"},
                    "coverage":[{"episode_id":"cards-no-youtube","scope":"full_episode"}]}]}
                """).andExpect(status().isOk());

        verifySeriesCards(admin);
        var missing = body(mvc.perform(post("/api/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seriesId\":\"cards-featured\",\"limit\":100}")).andExpect(status().isOk()));
        assertThat(missing.path("recommendedMovies").valueStream().map(item -> item.path("id").asText()))
                .doesNotContain("cards-feature");
        mvc.perform(auth(put("/api/users/watch-progress/cards-feature"), admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"cards-video\",\"positionMs\":0,\"expectedVersion\":-1}"))
                .andExpect(status().isNotFound());

        Files.writeString(mediaRoot.resolve("cards.mp4"), "test video");
        verifyMovieAndRecommendationArtwork();
        verifyProgressThumbnails();
    }

    @SuppressWarnings("resource") // SessionFactory là của Spring, đóng là sập context test
    private void verifySeriesCards(String admin) throws Exception {
        var request = get(CATALOG + "series/cards");
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            long before = statistics.getPrepareStatementCount();
            JsonNode cards = body(mvc.perform(request).andExpect(status().isOk()));
            assertThat(statistics.getPrepareStatementCount() - before).isEqualTo(1);
            assertThat(cards.isArray()).isTrue();
            var fixtureCards = cards.valueStream().filter(card -> card.path("id").asText().startsWith("cards-")).toList();
            assertThat(fixtureCards).hasSize(2);
            assertThat(fixtureCards.getFirst()).isEqualTo(mapper.readTree("""
                    {"id":"cards-featured","title":"W6 Z","originalTitle":"Featured","synopsis":"Cards fixture",
                     "releaseYearStart":2011,"releaseYearEnd":null,
                     "genres":[{"id":"cards-vietnamese","label":"Hài"},{"id":"cards-english","label":"Family"}],
                     "seasonCount":2,"episodeCount":3,"posterUrl":"https://example.com/cards.jpg",
                     "backdropUrl":"https://i.ytimg.com/vi/video_00001/maxresdefault.jpg","averageRating":null,"ratingCount":0}
                    """));
            assertThat(fixtureCards.getLast()).isEqualTo(mapper.readTree("""
                    {"id":"cards-empty","title":"W6 a","originalTitle":null,"synopsis":null,
                     "releaseYearStart":null,"releaseYearEnd":null,"genres":[],"seasonCount":0,"episodeCount":0,
                     "posterUrl":null,"backdropUrl":null,"averageRating":null,"ratingCount":0}
                    """));
            assertThat(body(mvc.perform(auth(get(CATALOG + "series/cards"), admin)).andExpect(status().isOk())))
                    .isEqualTo(cards);
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
        }
    }

    private void verifyMovieAndRecommendationArtwork() throws Exception {
        String poster = "https://i.ytimg.com/vi/video_00001/hqdefault.jpg";
        String backdrop = "https://i.ytimg.com/vi/video_00001/maxresdefault.jpg";
        mvc.perform(get("/api/movies/cards-featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.series_title").value("W6 Z"))
                .andExpect(jsonPath("$.data.poster").value("https://example.com/cards.jpg"))
                .andExpect(jsonPath("$.data.backdrop").value(backdrop));
        mvc.perform(get("/api/movies/cards-feature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.series_title").value("W6 Z"))
                .andExpect(jsonPath("$.data.poster").value(poster))
                .andExpect(jsonPath("$.data.backdrop").value(backdrop));
        mvc.perform(get("/api/movies").param("q", "Cards featured episode"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].poster").value(poster));
        mvc.perform(get("/api/movies/browse").param("q", "Cards featured episode"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].poster").value(poster));
        mvc.perform(post("/api/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"genres\":[\"cards-vietnamese\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedMovies[?(@.id == 'cards-feature')].poster", contains(poster)))
                .andExpect(jsonPath("$.recommendedMovies[?(@.id == 'cards-feature')].backdrop", contains(backdrop)));
    }

    private void verifyProgressThumbnails() throws Exception {
        String member = token("cards-progress", Role.ROLE_USER);
        String endpoint = "/api/users/watch-progress";
        String poster = "https://i.ytimg.com/vi/video_00001/hqdefault.jpg";
        mvc.perform(auth(put(endpoint + "/cards-feature"), member).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"cards-video\",\"positionMs\":0,\"expectedVersion\":-1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.thumbnailUrl").value(poster));
        mvc.perform(auth(put(endpoint + "/cards-no-youtube"), member).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetId\":\"cards-fallback-video\",\"positionMs\":0,\"expectedVersion\":-1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thumbnailUrl").value("https://example.com/cards.jpg"));
        mvc.perform(auth(get(endpoint + "/cards-feature"), member))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.thumbnailUrl").value(poster));
        mvc.perform(auth(get(endpoint), member)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.episodeId == 'cards-feature')].thumbnailUrl", contains(poster)));
    }

    static String jadooFixture() throws Exception {
        return Files.readString(Path.of("../../data/catalog-by-series/catalog.jadoo.json"));
    }

    @Test
    void ranksExactSearchResultsAboveFuzzyResults() throws Exception {
        String admin = token("catalog-fuzzy", Role.ROLE_ADMIN);
        submit("import", admin, """
                {"package":{"id":"fuzzy-ranking"},
                 "series":[{"id":"typo-series","title":"Zeppar"},{"id":"exact-series","title":"Zepar"}],
                 "episodes":[{"id":"typo-episode","series_id":"typo-series","title":"Different title"},
                             {"id":"exact-episode","series_id":"exact-series","title":"Exact series episode"}]}
                """).andExpect(status().isOk());

        mvc.perform(get(CATALOG + "search").param("q", "zepar")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains("exact-series", "exact-episode", "typo-series", "typo-episode")));
        mvc.perform(get("/api/movies/browse").param("query", "zepar").param("sort", "rating"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id",
                        contains("exact-episode", "exact-series", "typo-episode", "typo-series")));
    }

    private String token(String username, Role role) {
        users.save(new User(username, username + "@example.com", passwords.encode("test-password"),
                username, role, LocalDateTime.now(clock)));
        return "Bearer " + jwt.issueLoginToken(username);
    }

    private ResultActions submit(String endpoint, String token, String body) throws Exception {
        return mvc.perform(auth(post(ADMIN + endpoint), token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode body(ResultActions response) throws Exception {
        return mapper.readTree(response.andReturn().getResponse().getContentAsByteArray());
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, token);
    }
}
