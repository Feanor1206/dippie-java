-- Lược đồ rút gọn từ 01_init.sql, giữ các cột và ràng buộc mà ứng dụng sử dụng.
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER' CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    token_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE movies (
    id VARCHAR(100) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    rating DOUBLE NOT NULL CHECK (rating BETWEEN 0 AND 10)
);

CREATE TABLE movie_ratings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    movie_id VARCHAR(100) NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE (user_id, movie_id)
);

CREATE TABLE catalog_series (
    id VARCHAR(100) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    original_title VARCHAR(255),
    synopsis LONGTEXT
);

CREATE TABLE catalog_seasons (
    id VARCHAR(100) PRIMARY KEY,
    series_id VARCHAR(100) NOT NULL REFERENCES catalog_series(id) ON DELETE CASCADE,
    number INT,
    title VARCHAR(255) NOT NULL,
    synopsis LONGTEXT,
    UNIQUE (series_id, id)
);

CREATE TABLE catalog_episodes (
    id VARCHAR(100) PRIMARY KEY,
    series_id VARCHAR(100) NOT NULL REFERENCES catalog_series(id) ON DELETE CASCADE,
    season_id VARCHAR(100),
    number INT,
    title VARCHAR(255) NOT NULL,
    kind VARCHAR(50) NOT NULL,
    synopsis LONGTEXT,
    duration_ms BIGINT CHECK (duration_ms > 0),
    record_json LONGTEXT,
    raw_json LONGTEXT,
    FOREIGN KEY (series_id, season_id) REFERENCES catalog_seasons(series_id, id)
);

CREATE TABLE catalog_media_assets (
    id VARCHAR(100) PRIMARY KEY,
    content_type VARCHAR(50) NOT NULL DEFAULT 'video',
    role VARCHAR(50) NOT NULL DEFAULT 'feature',
    storage_provider VARCHAR(50) NOT NULL,
    storage_key VARCHAR(100) NOT NULL,
    relative_path VARCHAR(500) NOT NULL,
    drive_file_id VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE catalog_episode_assets (
    episode_id VARCHAR(100) NOT NULL REFERENCES catalog_episodes(id) ON DELETE CASCADE,
    media_asset_id VARCHAR(100) NOT NULL REFERENCES catalog_media_assets(id) ON DELETE CASCADE,
    PRIMARY KEY (episode_id, media_asset_id)
);

CREATE TABLE watch_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    episode_id VARCHAR(100) NOT NULL REFERENCES catalog_episodes(id) ON DELETE CASCADE,
    asset_id VARCHAR(100) NOT NULL,
    position_ms BIGINT NOT NULL CHECK (position_ms >= 0),
    opt_lock_version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE (user_id, episode_id)
);

CREATE TABLE admin_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    actor_username VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(100) NOT NULL,
    details_json LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
