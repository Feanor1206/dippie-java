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
    parent_mode_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    parent_pin_hash VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE child_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    avatar_key VARCHAR(32) NOT NULL,
    daily_minutes INT NOT NULL DEFAULT 60 CHECK (daily_minutes BETWEEN 0 AND 600),
    math_level INT NOT NULL DEFAULT 1 CHECK (math_level BETWEEN 1 AND 4),
    reward_minutes INT NOT NULL DEFAULT 5 CHECK (reward_minutes BETWEEN 1 AND 30),
    max_bonus_minutes INT NOT NULL DEFAULT 30 CHECK (max_bonus_minutes BETWEEN 0 AND 120),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE screen_time_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
    usage_day DATE NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('WATCH', 'REWARD', 'PARENT_GRANT')),
    seconds INT NOT NULL CHECK (seconds > 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE movies (
    id VARCHAR(100) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    rating DOUBLE NOT NULL CHECK (rating BETWEEN 0 AND 10)
);

CREATE TABLE movie_ratings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    profile_id BIGINT REFERENCES child_profiles(id) ON DELETE CASCADE,
    profile_scope_id BIGINT NOT NULL DEFAULT 0,
    movie_id VARCHAR(100) NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE (user_id, profile_scope_id, movie_id)
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
    profile_id BIGINT REFERENCES child_profiles(id) ON DELETE CASCADE,
    profile_scope_id BIGINT NOT NULL DEFAULT 0,
    episode_id VARCHAR(100) NOT NULL REFERENCES catalog_episodes(id) ON DELETE CASCADE,
    asset_id VARCHAR(100) NOT NULL,
    position_ms BIGINT NOT NULL CHECK (position_ms >= 0),
    opt_lock_version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE (user_id, profile_scope_id, episode_id)
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
