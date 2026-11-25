-- Bổ sung 10 chỉ mục tối ưu hiệu năng truy vấn theo kiểu lũy đẳng trên MySQL 8 (tương ứng migration thủ công 14_query_performance_indexes.sql).

SET @idx_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'users' AND index_name = 'idx_users_role_enabled'),
    'CREATE INDEX idx_users_role_enabled ON users (role, enabled)', 'DO 0'
);
PREPARE stmt FROM @idx_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'favorite_items' AND index_name = 'idx_favorite_user_added'),
    'CREATE INDEX idx_favorite_user_added ON favorite_items (user_id, added_at)', 'DO 0'
);
PREPARE stmt FROM @idx_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'watchlist_items' AND index_name = 'idx_watchlist_user_added'),
    'CREATE INDEX idx_watchlist_user_added ON watchlist_items (user_id, added_at)', 'DO 0'
);
PREPARE stmt FROM @idx_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'movie_ratings' AND index_name = 'idx_ratings_user_updated'),
    'CREATE INDEX idx_ratings_user_updated ON movie_ratings (user_id, updated_at)', 'DO 0'
);
PREPARE stmt FROM @idx_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'watch_progress' AND index_name = 'idx_watch_progress_episode'),
    'CREATE INDEX idx_watch_progress_episode ON watch_progress (episode_id)', 'DO 0'
);
PREPARE stmt FROM @idx_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'catalog_snapshots' AND index_name = 'idx_cat_snap_hash_version'),
    'CREATE INDEX idx_cat_snap_hash_version ON catalog_snapshots (content_hash, snapshot_version)', 'DO 0'
);
PREPARE stmt FROM @idx_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'catalog_packages' AND index_name = 'idx_cat_pkg_imported'),
    'CREATE INDEX idx_cat_pkg_imported ON catalog_packages (imported_at)', 'DO 0'
);
PREPARE stmt FROM @idx_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'catalog_seasons' AND index_name = 'idx_cat_season_series_num'),
    'CREATE INDEX idx_cat_season_series_num ON catalog_seasons (series_id, number)', 'DO 0'
);
PREPARE stmt FROM @idx_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'catalog_scenes' AND index_name = 'idx_cat_scene_ep_start'),
    'CREATE INDEX idx_cat_scene_ep_start ON catalog_scenes (episode_id, start_ms)', 'DO 0'
);
PREPARE stmt FROM @idx_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_sql = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'catalog_episodes' AND index_name = 'idx_cat_ep_series_pub_num'),
    'CREATE INDEX idx_cat_ep_series_pub_num ON catalog_episodes (series_id, publication_status, number)', 'DO 0'
);
PREPARE stmt FROM @idx_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
