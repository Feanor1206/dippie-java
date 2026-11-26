package com.dippie.desktop;

import com.dippie.desktop.dao.*;
import com.dippie.desktop.db.DataAccessException;
import com.dippie.desktop.model.Role;
import com.dippie.desktop.model.catalog.CatalogRows.EpisodeAsset;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// test từng DAO chạy SQL thật trên H2 in-memory
class DaoTest extends TestDatabase {
    @Test
    void usersLoadAccountsAndCredentialsSeparately() {
        var users = new UserDao(db);
        assertEquals(3, users.count());
        assertEquals(Role.ROLE_USER, users.findById(2L).orElseThrow().role());
        assertEquals("admin", users.findCredentials("admin@dippie.test").orElseThrow().account().username());
        assertTrue(users.findCredentials("' OR '1'='1").isEmpty());
    }

    @Test
    void seriesRetainsOriginalTitleAndSynopsis() {
        var series = new SeriesDao(db).findById("s1").orElseThrow();
        assertEquals("First Series", series.originalTitle());
        assertEquals("Tóm tắt bộ một", series.synopsis());
    }

    @Test
    void seasonMapsParentAndNumber() {
        var season = new SeasonDao(db).findById("m2").orElseThrow();
        assertEquals("s1", season.seriesId());
        assertEquals(2, season.number());
    }

    @Test
    void episodesPreserveNullableSeasonAndDuration() {
        var episodes = new EpisodeDao(db);
        assertEquals(125000L, episodes.findById("e1").orElseThrow().durationMs());
        var special = episodes.findById("e3").orElseThrow();
        assertNull(special.seasonId());
        assertNull(special.durationMs());
    }

    @Test
    void driveSourcesIgnoreDisabledAssets() {
        var assets = new MediaAssetDao(db);
        assertEquals("abc_123", assets.findById("a1").orElseThrow().driveFileId());
        var sources = db.read(assets::findDriveAssets);
        assertEquals(List.of("a1"), sources.stream().map(asset -> asset.id()).toList());
    }

    @Test
    void episodeAssetLinksUseBothPartsOfThePrimaryKey() {
        var episodeAssets = new EpisodeAssetDao(db);
        var key = new EpisodeAsset("e1", "a1");
        assertEquals(key, episodeAssets.findById(key).orElseThrow());
        assertTrue(episodeAssets.findById(new EpisodeAsset("e2", "a1")).isEmpty());
        assertEquals(2, episodeAssets.findAll().size());
    }

    @Test
    void moviesSupplyTitlesForCatalogAndLegacyRatings() {
        var movies = new MovieDao(db);
        assertEquals("Phim cũ", movies.findById("legacy").orElseThrow().title());
        assertEquals(List.of("e1", "e2", "legacy", "s1"),
                db.read(movies::findRatedMovies).stream().map(movie -> movie.id()).toList());
    }

    @Test
    void ratingsLinkEpisodeAndSeriesAndKeepLegacyRows() {
        var ratings = new RatingDao(db);
        assertEquals(10, ratings.findById(1L).orElseThrow().score());
        var rows = db.read(ratings::findContexts);
        assertEquals("s1", rows.stream().filter(row -> row.id() == 1).findFirst().orElseThrow().seriesId());
        assertEquals("s1", rows.stream().filter(row -> row.id() == 3).findFirst().orElseThrow().seriesId());
        var legacy = rows.stream().filter(row -> row.id() == 5).findFirst().orElseThrow();
        assertNull(legacy.seriesId());
    }

    @Test
    void watchProgressCountsRowsAndRanksByDistinctViewersWithPositiveProgress() {
        var progress = new WatchProgressDao(db);
        assertEquals(4, progress.count());
        var stats = db.read(progress::mostWatchedSeries);
        assertEquals(1, stats.size());
        assertEquals("s1", stats.getFirst().seriesId());
        assertEquals(2, stats.getFirst().viewers());
    }

    @Test
    void auditWritesAndReadsTheRealEventFields() {
        var audit = new AuditDao(db);
        db.transaction(connection -> {
            audit.insert(connection, 1L, "admin", "USER_DISABLED", "USER", "2", "{\"enabled\":false}");
            return null;
        });
        var event = audit.findAll().getFirst();
        assertEquals(1L, event.actorUserId());
        assertEquals("admin", event.actorUsername());
        assertEquals("USER_DISABLED", event.action());
        assertEquals("2", event.targetId());
        assertEquals("{\"enabled\":false}", event.detailsJson());
        assertNotNull(event.createdAt());
    }

    @Test
    void invalidSqlUsesVietnameseDataAccessException() throws Exception {
        execute("ALTER TABLE catalog_series RENAME TO missing_series");
        var failure = assertThrows(DataAccessException.Query.class, () -> new SeriesDao(db).findAll());
        assertTrue(failure.getMessage().contains("cơ sở dữ liệu"));
    }
}
