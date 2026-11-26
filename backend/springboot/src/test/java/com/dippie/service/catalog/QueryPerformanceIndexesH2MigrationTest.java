package com.dippie.service.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Chạy thử script 14_query_performance_indexes.sql trên H2 chế độ MySQL hai lần liền để chắc tạo index lặp lại không lỗi.
class QueryPerformanceIndexesH2MigrationTest {

    @Test
    void createsAllQueryIndexesIdempotentlyOnH2() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:idx-migration-14;MODE=MySQL", "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, role VARCHAR(20), enabled BOOLEAN)");
            statement.execute("CREATE TABLE favorite_items (id BIGINT PRIMARY KEY, user_id BIGINT, added_at TIMESTAMP)");
            statement.execute("CREATE TABLE watchlist_items (id BIGINT PRIMARY KEY, user_id BIGINT, added_at TIMESTAMP)");
            statement.execute("CREATE TABLE movie_ratings (id BIGINT PRIMARY KEY, user_id BIGINT, updated_at TIMESTAMP)");
            statement.execute("CREATE TABLE watch_progress (id BIGINT PRIMARY KEY, episode_id VARCHAR(100))");
            statement.execute("CREATE TABLE catalog_snapshots (id VARCHAR(100) PRIMARY KEY, content_hash VARCHAR(100), snapshot_version BIGINT)");
            statement.execute("CREATE TABLE catalog_packages (id VARCHAR(100) PRIMARY KEY, imported_at TIMESTAMP)");
            statement.execute("CREATE TABLE catalog_seasons (id VARCHAR(100) PRIMARY KEY, series_id VARCHAR(100), number INT)");
            statement.execute("CREATE TABLE catalog_scenes (id VARCHAR(100) PRIMARY KEY, episode_id VARCHAR(100), start_ms BIGINT)");
            statement.execute("CREATE TABLE catalog_episodes (id VARCHAR(100) PRIMARY KEY, series_id VARCHAR(100), publication_status VARCHAR(16), number INT)");

            // Chạy cùng file SQL hai lần liên tiếp để kiểm tra tính idempotent.
            var migration = new FileSystemResource(Path.of("../../database/migrations/14_query_performance_indexes.sql"));
            ScriptUtils.executeSqlScript(connection, migration);
            ScriptUtils.executeSqlScript(connection, migration);

            Set<String> indexes = new HashSet<>();
            try (var rows = statement.executeQuery("SELECT LOWER(INDEX_NAME) FROM INFORMATION_SCHEMA.INDEXES")) {
                while (rows.next()) {
                    indexes.add(rows.getString(1));
                }
            }
            assertThat(indexes).contains(
                    "idx_users_role_enabled",
                    "idx_favorite_user_added",
                    "idx_watchlist_user_added",
                    "idx_ratings_user_updated",
                    "idx_watch_progress_episode",
                    "idx_cat_snap_hash_version",
                    "idx_cat_pkg_imported",
                    "idx_cat_season_series_num",
                    "idx_cat_scene_ep_start",
                    "idx_cat_ep_series_pub_num"
            );
        }
    }
}
