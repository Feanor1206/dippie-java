package com.dippie.service.catalog;

import com.dippie.dto.catalog.CatalogEpisodeView;
import com.dippie.dto.media.MediaAssetView;
import com.dippie.dto.movie.MovieView;
import com.dippie.model.catalog.AuditLog;
import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogEpisodeAsset;
import com.dippie.model.catalog.CatalogMediaAsset;
import com.dippie.model.catalog.CatalogScene;
import com.dippie.model.catalog.CatalogSeason;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.catalog.CatalogSource;
import com.dippie.model.media.MediaAsset;
import com.dippie.model.movie.Movie;
import com.dippie.repository.catalog.CatalogEpisodeRepository;
import com.dippie.repository.catalog.CatalogMediaAssetRepository;
import com.dippie.repository.catalog.CatalogSceneRepository;
import com.dippie.repository.catalog.CatalogSeasonRepository;
import com.dippie.repository.catalog.CatalogSeriesRepository;
import com.dippie.repository.media.MediaAssetRepository;
import com.dippie.repository.movie.MovieRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @DataJpaTest profile test check lưu cây catalog, khóa ngoại kép series/season, đồng bộ publication_status với JSON/DATETIME
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CatalogPersistenceTest {

    private static final String READY = "{\"status\":\"Ready\"}";
    private final TestEntityManager entities;
    private final CatalogSeriesRepository series;
    private final CatalogSeasonRepository seasons;
    private final CatalogEpisodeRepository episodes;
    private final CatalogMediaAssetRepository assets;
    private final CatalogSceneRepository scenes;
    private final MovieRepository movies;
    private final MediaAssetRepository media;
    private final JsonMapper json = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Autowired
    CatalogPersistenceTest(TestEntityManager entities, CatalogSeriesRepository series, CatalogSeasonRepository seasons,
                           CatalogEpisodeRepository episodes, CatalogMediaAssetRepository assets,
                           CatalogSceneRepository scenes, MovieRepository movies, MediaAssetRepository media) {
        this.entities = entities;
        this.series = series;
        this.seasons = seasons;
        this.episodes = episodes;
        this.assets = assets;
        this.scenes = scenes;
        this.movies = movies;
        this.media = media;
    }

    @Test
    void savesAndReloadsPublishedCatalogAndSynchronizesPublicationOnUpdate() {
        // lưu đủ cây series, season, episode, asset, bảng nối với phân cảnh rồi xóa cache EntityManager cho nó đọc lại từ DB thật
        CatalogSeries savedSeries = series.saveAndFlush(series("series-1"));
        seasons.saveAndFlush(season("season-1", savedSeries.getId()));
        episodes.saveAndFlush(episode(savedSeries.getId(), "season-1", READY));

        String coverage = "[{\"episode_id\":\"episode-1\",\"scope\":\"full_episode\"}]";
        assets.saveAndFlush(new CatalogMediaAsset("asset-1", "video", "feature", null, null, null,
                "local", "library", "episode.mp4", null, null, 1024L, null, 60_000L,
                null, coverage, null, "public", true, READY, null, 1));
        entities.persistAndFlush(new CatalogEpisodeAsset("episode-1", "asset-1"));
        scenes.saveAndFlush(new CatalogScene("scene-1", "asset-1", "episode-1", null, 1_000, 2_000,
                "Opening", null, null, null, null, null, null, null, null, null, null, READY, null, 1));
        entities.clear();

        CatalogSeries reloadedSeries = series.findById("series-1").orElseThrow();
        assertThat(reloadedSeries.getSynopsis()).hasSize(70_000);
        assertThat(reloadedSeries.getCreatedAt()).isNotNull();
        assertThat(reloadedSeries.getUpdatedAt()).isNotNull();
        assertThat(seasons.findBySeriesIdOrderByNumberAsc("series-1"))
                .extracting(CatalogSeason::getId).containsExactly("season-1");
        assertThat(entities.find(CatalogEpisodeAsset.class, new CatalogEpisodeAsset.Key("episode-1", "asset-1")))
                .isNotNull();
        assertThat(assets.findByEpisodeId("episode-1"))
                .singleElement().satisfies(asset -> assertThat(asset.coversFullEpisode("episode-1")).isTrue());
        assertThat(scenes.findByEpisodeIdOrderByStartMsAsc("episode-1"))
                .extracting(CatalogScene::getId).containsExactly("scene-1");

        CatalogEpisode reloaded = episodes.findBySeriesIdAndPublicationStatusOrderByNumberAsc("series-1", "ready").getFirst();
        assertThat(reloaded.getPublicationStatus()).isEqualTo("ready");
        assertThat(reloaded.getOptLockVersion()).isZero();
        JsonNode view = json.valueToTree(CatalogEpisodeView.from(reloaded));
        assertThat(view.path("releaseDate").asText()).isEqualTo("2025-01-01");
        assertThat(view.path("seasonId").asText()).isEqualTo("season-1");
        assertThat(view.path("synopsis").isNull()).isTrue();
        assertThat(view.has("publicationStatus")).isFalse();

        // đổi recordJson sang archived rồi flush xem publication_status với optLockVersion có tự nhảy theo không
        reloaded.updateRecordJson("{\"status\":\"archived\"}");
        assertThat(reloaded.getPublicationStatus()).isEqualTo("archived");
        entities.flush();
        entities.clear();

        CatalogEpisode updated = episodes.findById("episode-1").orElseThrow();
        assertThat(updated.getPublicationStatus()).isEqualTo("archived");
        assertThat(updated.getOptLockVersion()).isEqualTo(1L);
        assertThat(episodes.findBySeriesIdAndPublicationStatusOrderByNumberAsc("series-1", "ready")).isEmpty();
    }

    // tập của series-1 mà cắm nhầm season bên series-2 thì khóa ngoại kép dưới DB phải chặn liền
    @Test
    void rejectsAnEpisodeLinkedToAnotherSeriesSeason() {
        series.saveAndFlush(series("series-1"));
        series.saveAndFlush(series("series-2"));
        seasons.saveAndFlush(season("season-2", "series-2"));

        assertThatThrownBy(() -> episodes.saveAndFlush(episode("series-1", "season-2", READY)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void preservesMovieAndMediaResponseFields() {
        Movie movie = new Movie("movie-1", "Film", 2025, 8.5, "90 min",
                "  Adventure, ,Drama, Adventure  ", null, null, null, null);
        movies.saveAndFlush(movie);
        media.saveAndFlush(new MediaAsset("media-1", movie.getId(), "feature", "local",
                null, "film.mp4", "v1", "video/mp4", 1024, null, true, true));
        entities.clear();

        JsonNode movieView = json.valueToTree(MovieView.from(movies.findById("movie-1").orElseThrow()));
        assertThat(movieView.path("year").intValue()).isEqualTo(2025);
        assertThat(movieView.path("genres")).extracting(JsonNode::textValue)
                .containsExactly("Adventure", "Drama", "Adventure");
        assertThat(movieView.path("videoUrl").isNull()).isTrue();

        MediaAsset asset = media.findByMovieIdAndAvailableTrueAndPublicAssetTrue("movie-1").getFirst();
        JsonNode mediaView = json.valueToTree(MediaAssetView.from(asset));
        assertThat(mediaView.path("assetId").asText()).isEqualTo("media-1");
        assertThat(mediaView.path("streamUrl").asText()).isEqualTo("/api/media/stream/media-1");
        assertThat(mediaView.path("isPublic").booleanValue()).isTrue();
        assertThat(mediaView.path("durationSeconds").isNull()).isTrue();
    }

    // giữ nguyên cấu trúc JSON của AuditLog với độ chính xác microsecond của retrievedAt bên CatalogSource
    @Test
    void preservesAuditJsonAndSourceDateTimePrecision() throws Exception {
        JsonNode oldValues = json.readTree("{\"title\":\"Original\"}");
        AuditLog entry = entities.persistAndFlush(new AuditLog("movies", "movie-1", "DELETE",
                oldValues, null, "test@localhost"));
        LocalDateTime retrievedAt = LocalDateTime.of(2025, 1, 1, 1, 2, 3, 456_789_000);
        entities.persistAndFlush(new CatalogSource("source-1", "manual", "Source", null, null,
                retrievedAt, null, null, 1));
        entities.clear();

        AuditLog reloaded = entities.find(AuditLog.class, entry.getId());
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getOldValues()).isEqualTo(oldValues);
        assertThat(reloaded.getNewValues()).isNull();
        assertThat(reloaded.getActionAt()).isNotNull();
        assertThat(entities.find(CatalogSource.class, "source-1").getRetrievedAt()).isEqualTo(retrievedAt);
    }

    private static CatalogSeries series(String id) {
        return new CatalogSeries(id, "Series", null, null, "x".repeat(70_000), null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                READY, null, 1);
    }

    private static CatalogSeason season(String id, String seriesId) {
        return new CatalogSeason(id, seriesId, 1, "Season One", null, "regular", null,
                null, null, null, READY, null, 1);
    }

    private static CatalogEpisode episode(String seriesId, String seasonId, String recordJson) {
        return new CatalogEpisode("episode-1", seriesId, seasonId, 1, "Episode One", null, null, null,
                "regular", LocalDate.of(2025, 1, 1), 60_000L, null, null, null, null,
                null, null, null, null, null, null, null, null, recordJson, null, 1);
    }
}
