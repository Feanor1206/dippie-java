-- Bản nâng cấp thủ công đồng bộ với Flyway V6 (chế độ hồ sơ trẻ em và tách dữ liệu theo hồ sơ).

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'parent_mode_enabled'
    ),
    'ALTER TABLE users ADD COLUMN parent_mode_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER terms_accepted_at',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'parent_pin_hash'
    ),
    'ALTER TABLE users ADD COLUMN parent_pin_hash VARCHAR(255) NULL AFTER parent_mode_enabled',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS child_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    avatar_key VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_child_profiles_user_created (user_id, created_at),
    CONSTRAINT fk_child_profile_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'watchlist_items' AND column_name = 'profile_id'
    ),
    'ALTER TABLE watchlist_items ADD COLUMN profile_id BIGINT NULL AFTER user_id',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'watchlist_items' AND column_name = 'profile_scope_id'
    ),
    'ALTER TABLE watchlist_items ADD COLUMN profile_scope_id BIGINT NOT NULL DEFAULT 0 AFTER profile_id',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'watchlist_items' AND constraint_name = 'fk_watchlist_profile'
    ),
    'ALTER TABLE watchlist_items ADD CONSTRAINT fk_watchlist_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'watchlist_items' AND index_name = 'uq_user_profile_movie_watchlist'
    ),
    'ALTER TABLE watchlist_items ADD CONSTRAINT uq_user_profile_movie_watchlist UNIQUE (user_id, profile_scope_id, movie_id)',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'watchlist_items' AND index_name = 'uq_user_movie_watchlist'
    ),
    'ALTER TABLE watchlist_items DROP INDEX uq_user_movie_watchlist',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'watchlist_items' AND index_name = 'idx_watchlist_user_profile_added'
    ),
    'CREATE INDEX idx_watchlist_user_profile_added ON watchlist_items (user_id, profile_scope_id, added_at)',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'favorite_items' AND column_name = 'profile_id'
    ),
    'ALTER TABLE favorite_items ADD COLUMN profile_id BIGINT NULL AFTER user_id',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'favorite_items' AND column_name = 'profile_scope_id'
    ),
    'ALTER TABLE favorite_items ADD COLUMN profile_scope_id BIGINT NOT NULL DEFAULT 0 AFTER profile_id',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'favorite_items' AND constraint_name = 'fk_favorite_profile'
    ),
    'ALTER TABLE favorite_items ADD CONSTRAINT fk_favorite_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'favorite_items' AND index_name = 'uq_user_profile_movie_favorite'
    ),
    'ALTER TABLE favorite_items ADD CONSTRAINT uq_user_profile_movie_favorite UNIQUE (user_id, profile_scope_id, movie_id)',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'favorite_items' AND index_name = 'uq_user_movie_favorite'
    ),
    'ALTER TABLE favorite_items DROP INDEX uq_user_movie_favorite',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'favorite_items' AND index_name = 'idx_favorite_user_profile_added'
    ),
    'CREATE INDEX idx_favorite_user_profile_added ON favorite_items (user_id, profile_scope_id, added_at)',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'movie_ratings' AND column_name = 'profile_id'
    ),
    'ALTER TABLE movie_ratings ADD COLUMN profile_id BIGINT NULL AFTER user_id',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'movie_ratings' AND column_name = 'profile_scope_id'
    ),
    'ALTER TABLE movie_ratings ADD COLUMN profile_scope_id BIGINT NOT NULL DEFAULT 0 AFTER profile_id',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'movie_ratings' AND constraint_name = 'fk_rating_profile'
    ),
    'ALTER TABLE movie_ratings ADD CONSTRAINT fk_rating_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'movie_ratings' AND index_name = 'uq_user_profile_movie_rating'
    ),
    'ALTER TABLE movie_ratings ADD CONSTRAINT uq_user_profile_movie_rating UNIQUE (user_id, profile_scope_id, movie_id)',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'movie_ratings' AND index_name = 'uq_user_movie_rating'
    ),
    'ALTER TABLE movie_ratings DROP INDEX uq_user_movie_rating',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'movie_ratings' AND index_name = 'idx_ratings_user_profile_updated'
    ),
    'CREATE INDEX idx_ratings_user_profile_updated ON movie_ratings (user_id, profile_scope_id, updated_at)',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'watch_progress' AND column_name = 'profile_id'
    ),
    'ALTER TABLE watch_progress ADD COLUMN profile_id BIGINT NULL AFTER user_id',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'watch_progress' AND column_name = 'profile_scope_id'
    ),
    'ALTER TABLE watch_progress ADD COLUMN profile_scope_id BIGINT NOT NULL DEFAULT 0 AFTER profile_id',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'watch_progress' AND constraint_name = 'fk_watch_progress_profile'
    ),
    'ALTER TABLE watch_progress ADD CONSTRAINT fk_watch_progress_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'watch_progress' AND index_name = 'uq_watch_progress_user_profile_episode'
    ),
    'ALTER TABLE watch_progress ADD CONSTRAINT uq_watch_progress_user_profile_episode UNIQUE (user_id, profile_scope_id, episode_id)',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'watch_progress' AND index_name = 'uq_watch_progress_user_episode'
    ),
    'ALTER TABLE watch_progress DROP INDEX uq_watch_progress_user_episode',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'watch_progress' AND index_name = 'idx_watch_progress_user_profile_updated'
    ),
    'CREATE INDEX idx_watch_progress_user_profile_updated ON watch_progress (user_id, profile_scope_id, updated_at)',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
