
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100),
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    token_version BIGINT NOT NULL DEFAULT 0,
    terms_accepted_at TIMESTAMP NULL,
    parent_mode_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    parent_pin_hash VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_role_enabled (role, enabled),
    CONSTRAINT ck_user_role CHECK (HEX(role) IN (HEX('ROLE_USER'), HEX('ROLE_ADMIN')))
);

CREATE TABLE IF NOT EXISTS child_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    avatar_key VARCHAR(32) NOT NULL,
    daily_minutes INT NULL DEFAULT 60,
    math_level INT NOT NULL DEFAULT 1,
    reward_minutes INT NOT NULL DEFAULT 5,
    max_bonus_minutes INT NOT NULL DEFAULT 30,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_child_profiles_user_created (user_id, created_at),
    CONSTRAINT fk_child_profile_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_child_profile_daily_minutes CHECK (daily_minutes IS NULL OR daily_minutes BETWEEN 0 AND 600),
    CONSTRAINT ck_child_profile_math_level CHECK (math_level BETWEEN 1 AND 4),
    CONSTRAINT ck_child_profile_reward_minutes CHECK (reward_minutes BETWEEN 1 AND 30),
    CONSTRAINT ck_child_profile_max_bonus_minutes CHECK (max_bonus_minutes BETWEEN 0 AND 120)
);

CREATE TABLE IF NOT EXISTS screen_time_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL,
    usage_day DATE NOT NULL,
    kind VARCHAR(20) NOT NULL,
    seconds INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_screen_time_ledger_profile_day (profile_id, usage_day, kind),
    INDEX idx_screen_time_ledger_profile_kind_created (profile_id, kind, created_at),
    CONSTRAINT fk_screen_time_ledger_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE,
    CONSTRAINT ck_screen_time_ledger_kind CHECK (HEX(kind) IN (HEX('WATCH'), HEX('REWARD'), HEX('PARENT_GRANT'), HEX('BOUNTY'), HEX('LOOT'))),
    CONSTRAINT ck_screen_time_ledger_seconds CHECK (seconds > 0)
);

CREATE TABLE IF NOT EXISTS bounty_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    reward_minutes INT NOT NULL,
    icon VARCHAR(32) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    repeatable_daily BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_bounty_tasks_user_active (user_id, active),
    CONSTRAINT fk_bounty_tasks_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_bounty_task_reward_minutes CHECK (reward_minutes BETWEEN 1 AND 120)
);

CREATE TABLE IF NOT EXISTS bounty_task_profiles (
    task_id BIGINT NOT NULL,
    profile_id BIGINT NOT NULL,
    PRIMARY KEY (task_id, profile_id),
    INDEX idx_bounty_task_profiles_profile (profile_id),
    CONSTRAINT fk_bounty_task_profiles_task FOREIGN KEY (task_id) REFERENCES bounty_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_bounty_task_profiles_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS bounty_submissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    profile_id BIGINT NOT NULL,
    usage_day DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    reward_minutes INT NOT NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at TIMESTAMP NULL,
    reject_note VARCHAR(255) NULL,
    INDEX idx_bounty_submissions_profile_day (profile_id, usage_day, status),
    INDEX idx_bounty_submissions_task_profile_status (task_id, profile_id, status),
    CONSTRAINT fk_bounty_submissions_task FOREIGN KEY (task_id) REFERENCES bounty_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_bounty_submissions_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE,
    CONSTRAINT ck_bounty_submission_status CHECK (HEX(status) IN (HEX('PENDING'), HEX('APPROVED'), HEX('REJECTED'))),
    CONSTRAINT ck_bounty_submission_reward_minutes CHECK (reward_minutes BETWEEN 1 AND 120)
);

CREATE TABLE IF NOT EXISTS weekend_vault_overrides (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL,
    week_start DATE NOT NULL,
    unlocked BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_weekend_vault_profile_week UNIQUE (profile_id, week_start),
    CONSTRAINT fk_weekend_vault_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS child_play_settings (
    profile_id BIGINT PRIMARY KEY,
    watch_pet_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    watch_pet_faint_minutes INT NOT NULL DEFAULT 20,
    watch_pet_rest_minutes INT NOT NULL DEFAULT 5,
    loot_drop_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    recap_to_next_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_child_play_settings_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE,
    CONSTRAINT ck_child_play_pet_faint_minutes CHECK (watch_pet_faint_minutes BETWEEN 10 AND 60),
    CONSTRAINT ck_child_play_pet_rest_minutes CHECK (watch_pet_rest_minutes BETWEEN 3 AND 15)
);

CREATE TABLE IF NOT EXISTS child_play_state (
    profile_id BIGINT PRIMARY KEY,
    continuous_watch_seconds INT NOT NULL DEFAULT 0,
    last_watch_heartbeat_at TIMESTAMP NULL,
    pet_fainted BOOLEAN NOT NULL DEFAULT FALSE,
    rest_lock_until TIMESTAMP NULL,
    watch_seconds_since_loot INT NOT NULL DEFAULT 0,
    next_loot_threshold_seconds INT NOT NULL DEFAULT 360,
    loot_combo INT NOT NULL DEFAULT 0,
    loot_drop_pending BOOLEAN NOT NULL DEFAULT FALSE,
    last_loot_issued_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_child_play_state_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE,
    CONSTRAINT ck_child_play_continuous_watch CHECK (continuous_watch_seconds >= 0),
    CONSTRAINT ck_child_play_loot_watch CHECK (watch_seconds_since_loot >= 0),
    CONSTRAINT ck_child_play_loot_threshold CHECK (next_loot_threshold_seconds BETWEEN 300 AND 420),
    CONSTRAINT ck_child_play_loot_combo CHECK (loot_combo >= 0)
);

CREATE TABLE IF NOT EXISTS child_episode_recap_gates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL,
    series_id VARCHAR(100) NOT NULL,
    source_episode_id VARCHAR(100) NOT NULL,
    target_episode_id VARCHAR(100) NOT NULL,
    unlocked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unlocked_at TIMESTAMP NULL,
    CONSTRAINT fk_recap_gate_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE,
    CONSTRAINT uq_recap_gate_profile_target UNIQUE (profile_id, target_episode_id),
    INDEX idx_recap_gate_profile_series (profile_id, series_id, unlocked)
);

CREATE TABLE IF NOT EXISTS movies (
    id VARCHAR(100) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    release_year INT NULL,
    rating DOUBLE NOT NULL,
    runtime VARCHAR(20),
    genres LONGTEXT,
    description LONGTEXT,
    poster VARCHAR(500),
    backdrop VARCHAR(500),
    video_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_movies_title (title),
    INDEX idx_movies_year (release_year),
    INDEX idx_movies_rating (rating),
    CONSTRAINT ck_movie_rating_bounds CHECK (rating BETWEEN 0 AND 10)
);

CREATE TABLE IF NOT EXISTS watchlist_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    profile_id BIGINT NULL,
    profile_scope_id BIGINT NOT NULL DEFAULT 0,
    movie_id VARCHAR(100) NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_watchlist_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_watchlist_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_profile_movie_watchlist UNIQUE (user_id, profile_scope_id, movie_id),
    INDEX idx_watchlist_movie (movie_id),
    INDEX idx_watchlist_user_added (user_id, added_at),
    INDEX idx_watchlist_user_profile_added (user_id, profile_scope_id, added_at),
    CONSTRAINT fk_watchlist_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS movie_ratings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    profile_id BIGINT NULL,
    profile_scope_id BIGINT NOT NULL DEFAULT 0,
    movie_id VARCHAR(100) NOT NULL,
    rating INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT ck_movie_rating_range CHECK (rating >= 1 AND rating <= 10),
    CONSTRAINT fk_rating_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_rating_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_profile_movie_rating UNIQUE (user_id, profile_scope_id, movie_id),
    INDEX idx_ratings_movie (movie_id),
    INDEX idx_ratings_user_updated (user_id, updated_at),
    INDEX idx_ratings_user_profile_updated (user_id, profile_scope_id, updated_at),
    CONSTRAINT fk_rating_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS favorite_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    profile_id BIGINT NULL,
    profile_scope_id BIGINT NOT NULL DEFAULT 0,
    movie_id VARCHAR(100) NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE,
    CONSTRAINT uq_user_profile_movie_favorite UNIQUE (user_id, profile_scope_id, movie_id),
    INDEX idx_favorite_movie (movie_id),
    INDEX idx_favorite_user_added (user_id, added_at),
    INDEX idx_favorite_user_profile_added (user_id, profile_scope_id, added_at),
    CONSTRAINT fk_favorite_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS media_assets (
    id VARCHAR(64) PRIMARY KEY,
    movie_id VARCHAR(100) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    storage_provider VARCHAR(30) NOT NULL DEFAULT 'local',
    storage_key VARCHAR(100),
    relative_path VARCHAR(500) NOT NULL,
    version VARCHAR(50) NOT NULL DEFAULT 'v1',
    mime_type VARCHAR(100) NOT NULL DEFAULT 'video/mp4',
    byte_length BIGINT NOT NULL,
    duration_seconds DOUBLE,
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_media_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    CONSTRAINT uq_movie_kind_version UNIQUE (movie_id, kind, version),
    CONSTRAINT ck_media_kind CHECK (HEX(kind) IN (HEX('feature'), HEX('trailer'), HEX('extra'), HEX('opening'), HEX('ending'), HEX('dub'), HEX('audio_description')))
);

CREATE TABLE IF NOT EXISTS catalog_packages (
    id VARCHAR(100) PRIMARY KEY,
    version VARCHAR(50) NOT NULL,
    revision INT NOT NULL,
    default_language VARCHAR(20) NOT NULL,
    import_mode VARCHAR(50) NOT NULL,
    reference_scope VARCHAR(50) NOT NULL,
    name VARCHAR(200),
    description LONGTEXT,
    content_hash VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    error_message LONGTEXT,
    opt_lock_version BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    imported_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cat_pkg_imported (imported_at),
    CONSTRAINT ck_catalog_package_status CHECK (HEX(status) IN (HEX('COMMITTED')))
);

CREATE TABLE IF NOT EXISTS catalog_genres (
    id VARCHAR(100) PRIMARY KEY,
    labels_json LONGTEXT NOT NULL,
    aliases_json LONGTEXT,
    description LONGTEXT,
    record_json LONGTEXT,
    raw_json LONGTEXT,
    revision INT NOT NULL DEFAULT 1,
    opt_lock_version BIGINT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS catalog_attribute_scales (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    definition LONGTEXT,
    minimum DOUBLE NOT NULL,
    maximum DOUBLE NOT NULL,
    levels_json LONGTEXT NOT NULL,
    version VARCHAR(50),
    source_ids_json LONGTEXT,
    record_json LONGTEXT,
    raw_json LONGTEXT,
    revision INT NOT NULL DEFAULT 1,
    opt_lock_version BIGINT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT ck_catalog_scale_bounds CHECK (minimum < maximum)
);

CREATE TABLE IF NOT EXISTS catalog_sources (
    id VARCHAR(100) PRIMARY KEY,
    kind VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    uri VARCHAR(1000),
    note LONGTEXT,
    retrieved_at DATETIME(6),
    record_json LONGTEXT,
    raw_json LONGTEXT,
    revision INT NOT NULL DEFAULT 1,
    opt_lock_version BIGINT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT ck_catalog_source_kind CHECK (HEX(kind) IN (HEX('manual'), HEX('official'), HEX('external_catalog'), HEX('subtitle'), HEX('transcript'), HEX('video_observation'), HEX('model_output'), HEX('other')))
);

CREATE TABLE IF NOT EXISTS catalog_series (
    id VARCHAR(100) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    original_title VARCHAR(255),
    aliases_json LONGTEXT,
    synopsis LONGTEXT,
    translations_json LONGTEXT,
    genre_ids_json LONGTEXT,
    keywords_json LONGTEXT,
    themes_json LONGTEXT,
    origin_countries_json LONGTEXT,
    original_languages_json LONGTEXT,
    release_year_start INT,
    release_year_end INT,
    release_status VARCHAR(50),
    character_ids_json LONGTEXT,
    artworks_json LONGTEXT,
    attributes_json LONGTEXT,
    ratings_json LONGTEXT,
    advisories_json LONGTEXT,
    credits_json LONGTEXT,
    external_ids_json LONGTEXT,
    record_json LONGTEXT,
    raw_json LONGTEXT,
    revision INT NOT NULL DEFAULT 1,
    opt_lock_version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cat_series_title (title),
    CONSTRAINT ck_catalog_series_years CHECK (release_year_start <= release_year_end)
);

CREATE TABLE IF NOT EXISTS catalog_seasons (
    id VARCHAR(100) PRIMARY KEY,
    series_id VARCHAR(100) NOT NULL,
    number INT,
    title VARCHAR(255) NOT NULL,
    synopsis LONGTEXT,
    kind VARCHAR(50),
    translations_json LONGTEXT,
    release_date DATE,
    artworks_json LONGTEXT,
    attributes_json LONGTEXT,
    record_json LONGTEXT,
    raw_json LONGTEXT,
    revision INT NOT NULL DEFAULT 1,
    opt_lock_version BIGINT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_season_series FOREIGN KEY (series_id) REFERENCES catalog_series(id) ON DELETE CASCADE,
    CONSTRAINT uq_cat_season_series_id UNIQUE (series_id, id),
    INDEX idx_cat_season_series_num (series_id, number),
    CONSTRAINT ck_catalog_season_kind CHECK (kind IS NULL OR HEX(kind) IN (HEX('regular'), HEX('specials'), HEX('collection'), HEX('unknown')))
);

CREATE TABLE IF NOT EXISTS catalog_episodes (
    id VARCHAR(100) PRIMARY KEY,
    series_id VARCHAR(100) NOT NULL,
    season_id VARCHAR(100),
    number INT,
    title VARCHAR(255) NOT NULL,
    aliases_json LONGTEXT,
    synopsis LONGTEXT,
    translations_json LONGTEXT,
    kind VARCHAR(50) NOT NULL,
    release_date DATE,
    duration_ms BIGINT,
    genre_ids_json LONGTEXT,
    primary_language VARCHAR(50),
    orders_json LONGTEXT,
    links_json LONGTEXT,
    character_ids_json LONGTEXT,
    plot_facts_json LONGTEXT,
    keywords_json LONGTEXT,
    themes_json LONGTEXT,
    attributes_json LONGTEXT,
    ratings_json LONGTEXT,
    artworks_json LONGTEXT,
    credits_json LONGTEXT,
    record_json LONGTEXT,
    publication_status VARCHAR(16) NOT NULL DEFAULT 'draft' CONSTRAINT ck_catalog_episode_publication CHECK (HEX(publication_status) IN (HEX('draft'), HEX('ready'), HEX('archived'))),
    raw_json LONGTEXT,
    revision INT NOT NULL DEFAULT 1,
    opt_lock_version BIGINT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_episode_series FOREIGN KEY (series_id) REFERENCES catalog_series(id) ON DELETE CASCADE,
    CONSTRAINT fk_episode_season_same_series FOREIGN KEY (series_id, season_id)
        REFERENCES catalog_seasons(series_id, id),
    INDEX idx_cat_ep_series_season (series_id, season_id),
    INDEX idx_cat_ep_season (season_id),
    INDEX idx_cat_ep_publication (publication_status),
    INDEX idx_cat_ep_status_series_duration (publication_status, series_id, duration_ms),
    INDEX idx_cat_ep_series_pub_num (series_id, publication_status, number),
    CONSTRAINT ck_catalog_episode_kind CHECK (HEX(kind) IN (HEX('regular'), HEX('special'), HEX('ova'), HEX('ona'), HEX('recap'), HEX('short'), HEX('pilot'), HEX('movie'), HEX('unknown'))),
    CONSTRAINT ck_catalog_episode_duration CHECK (duration_ms > 0)
);

CREATE TABLE IF NOT EXISTS catalog_segments (
    id VARCHAR(100) PRIMARY KEY,
    episode_id VARCHAR(100) NOT NULL,
    position INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    aliases_json LONGTEXT,
    synopsis LONGTEXT,
    translations_json LONGTEXT,
    primary_language VARCHAR(50),
    character_ids_json LONGTEXT,
    plot_facts_json LONGTEXT,
    keywords_json LONGTEXT,
    themes_json LONGTEXT,
    attributes_json LONGTEXT,
    episode_start_ms BIGINT,
    episode_end_ms BIGINT,
    record_json LONGTEXT,
    raw_json LONGTEXT,
    revision INT NOT NULL DEFAULT 1,
    opt_lock_version BIGINT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_segment_episode FOREIGN KEY (episode_id) REFERENCES catalog_episodes(id) ON DELETE CASCADE,
    UNIQUE KEY uq_cat_segment_position (episode_id, position),
    CONSTRAINT ck_cat_segment_time_range CHECK (
        (episode_start_ms IS NULL AND episode_end_ms IS NULL)
        OR (episode_start_ms IS NOT NULL AND episode_end_ms IS NOT NULL
            AND episode_start_ms >= 0 AND episode_end_ms > episode_start_ms)
    )
);

CREATE TABLE IF NOT EXISTS catalog_characters (
    id VARCHAR(100) PRIMARY KEY,
    series_ids_json LONGTEXT,
    name VARCHAR(255) NOT NULL,
    aliases_json LONGTEXT,
    description LONGTEXT,
    role VARCHAR(50),
    traits_json LONGTEXT,
    relationships_json LONGTEXT,
    voice_credits_json LONGTEXT,
    artworks_json LONGTEXT,
    record_json LONGTEXT,
    raw_json LONGTEXT,
    revision INT NOT NULL DEFAULT 1,
    opt_lock_version BIGINT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS catalog_episode_characters (
    episode_id VARCHAR(100) NOT NULL,
    character_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (episode_id, character_id),
    INDEX idx_cat_ep_character (character_id),
    FOREIGN KEY (episode_id) REFERENCES catalog_episodes(id) ON DELETE CASCADE,
    FOREIGN KEY (character_id) REFERENCES catalog_characters(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS catalog_episode_genres (
    episode_id VARCHAR(100) NOT NULL,
    genre_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (episode_id, genre_id),
    INDEX idx_cat_ep_genre (genre_id),
    FOREIGN KEY (episode_id) REFERENCES catalog_episodes(id) ON DELETE CASCADE,
    FOREIGN KEY (genre_id) REFERENCES catalog_genres(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS catalog_media_assets (
    id VARCHAR(100) PRIMARY KEY,
    content_type VARCHAR(50) NOT NULL DEFAULT 'video',
    role VARCHAR(50) NOT NULL DEFAULT 'feature',
    edition_label VARCHAR(100),
    spoken_languages_json LONGTEXT,
    embedded_subtitle_languages_json LONGTEXT,
    storage_provider VARCHAR(50) NOT NULL,
    storage_key VARCHAR(100) NOT NULL,
    relative_path VARCHAR(500) NOT NULL,
    drive_file_id VARCHAR(255),
    drive_folder_id VARCHAR(255),
    size_bytes BIGINT,
    sha256 VARCHAR(100),
    duration_ms BIGINT,
    technical_json LONGTEXT,
    coverage_json LONGTEXT,
    related_asset_id VARCHAR(100),
    access_policy VARCHAR(50) NOT NULL DEFAULT 'public',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    record_json LONGTEXT,
    raw_json LONGTEXT,
    revision INT NOT NULL DEFAULT 1,
    opt_lock_version BIGINT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_asset_related FOREIGN KEY (related_asset_id) REFERENCES catalog_media_assets(id) ON DELETE SET NULL,
    CONSTRAINT ck_catalog_asset_policy CHECK (HEX(access_policy) IN (HEX('private'), HEX('authenticated'), HEX('public'))),
    CONSTRAINT ck_catalog_asset_content CHECK (HEX(content_type) IN (HEX('video'), HEX('audio'))),
    CONSTRAINT ck_catalog_asset_role CHECK (HEX(role) IN (HEX('feature'), HEX('trailer'), HEX('extra'), HEX('opening'), HEX('ending'), HEX('dub'), HEX('audio_description'))),
    CONSTRAINT ck_catalog_asset_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_catalog_asset_duration CHECK (duration_ms > 0)
);

CREATE TABLE IF NOT EXISTS catalog_episode_assets (
    episode_id VARCHAR(100) NOT NULL,
    media_asset_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (episode_id, media_asset_id),
    INDEX idx_cat_episode_assets_media (media_asset_id),
    CONSTRAINT fk_cat_ep_asset_ep FOREIGN KEY (episode_id) REFERENCES catalog_episodes(id) ON DELETE CASCADE,
    CONSTRAINT fk_cat_ep_asset_media FOREIGN KEY (media_asset_id) REFERENCES catalog_media_assets(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS catalog_text_tracks (
    id VARCHAR(100) PRIMARY KEY,
    media_asset_id VARCHAR(100),
    episode_id VARCHAR(100),
    source_id VARCHAR(100),
    language VARCHAR(50) NOT NULL,
    kind VARCHAR(50) NOT NULL,
    label VARCHAR(255),
    format VARCHAR(50) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    external_file_json LONGTEXT,
    cues_json LONGTEXT,
    provenance_json LONGTEXT,
    record_json LONGTEXT,
    raw_json LONGTEXT,
    revision INT NOT NULL DEFAULT 1,
    opt_lock_version BIGINT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_text_track_asset FOREIGN KEY (media_asset_id) REFERENCES catalog_media_assets(id) ON DELETE CASCADE,
    CONSTRAINT fk_cat_track_episode FOREIGN KEY (episode_id) REFERENCES catalog_episodes(id) ON DELETE CASCADE,
    CONSTRAINT fk_cat_track_source FOREIGN KEY (source_id) REFERENCES catalog_sources(id),
    CONSTRAINT ck_cat_text_track_origin CHECK (
        media_asset_id IS NOT NULL OR (episode_id IS NOT NULL AND source_id IS NOT NULL)
    ),
    INDEX idx_cat_track_asset (media_asset_id),
    INDEX idx_cat_track_episode (episode_id),
    CONSTRAINT ck_catalog_track_kind CHECK (HEX(kind) IN (HEX('subtitle'), HEX('transcript'), HEX('ocr')))
);

CREATE TABLE IF NOT EXISTS catalog_scenes (
    id VARCHAR(100) PRIMARY KEY,
    media_asset_id VARCHAR(100),
    episode_id VARCHAR(100) NOT NULL,
    segment_id VARCHAR(100),
    start_ms BIGINT NOT NULL,
    end_ms BIGINT NOT NULL,
    title VARCHAR(255),
    summary LONGTEXT,
    visual_description LONGTEXT,
    visual_tags_json LONGTEXT,
    sound_events_json LONGTEXT,
    transcript_cue_ids_json LONGTEXT,
    character_ids_json LONGTEXT,
    keywords_json LONGTEXT,
    themes_json LONGTEXT,
    attributes_json LONGTEXT,
    provenance_json LONGTEXT,
    record_json LONGTEXT,
    raw_json LONGTEXT,
    revision INT NOT NULL DEFAULT 1,
    opt_lock_version BIGINT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_scene_episode FOREIGN KEY (episode_id) REFERENCES catalog_episodes(id) ON DELETE CASCADE,
    INDEX idx_cat_scene_ep (episode_id),
    INDEX idx_cat_scene_ep_start (episode_id, start_ms),
    INDEX idx_cat_scene_asset (media_asset_id),
    CONSTRAINT fk_scene_asset FOREIGN KEY (media_asset_id) REFERENCES catalog_media_assets(id) ON DELETE CASCADE,
    CONSTRAINT fk_scene_segment FOREIGN KEY (segment_id) REFERENCES catalog_segments(id) ON DELETE SET NULL,
    CONSTRAINT ck_catalog_scene_time_range CHECK (start_ms >= 0 AND end_ms > start_ms)
);

CREATE TABLE IF NOT EXISTS catalog_snapshots (
    id VARCHAR(100) PRIMARY KEY,
    snapshot_version BIGINT NOT NULL,
    content_hash VARCHAR(100) NOT NULL,
    total_items INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    retry_count INT DEFAULT 0,
    error_message LONGTEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cat_snap_status (status),
    INDEX idx_cat_snap_hash_version (content_hash, snapshot_version),
    UNIQUE INDEX uq_cat_snap_version (snapshot_version),
    CONSTRAINT ck_catalog_snapshot_status CHECK (HEX(status) IN (HEX('PENDING'), HEX('BUILDING'), HEX('READY'), HEX('BLOCKED'), HEX('SYNC_FAILED')))
);

CREATE TABLE IF NOT EXISTS watch_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    profile_id BIGINT NULL,
    profile_scope_id BIGINT NOT NULL DEFAULT 0,
    episode_id VARCHAR(100) NOT NULL,
    asset_id VARCHAR(100) NOT NULL,
    position_ms BIGINT NOT NULL,
    opt_lock_version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT ck_watch_progress_position CHECK (position_ms >= 0),
    CONSTRAINT fk_watch_progress_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_watch_progress_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_watch_progress_episode FOREIGN KEY (episode_id) REFERENCES catalog_episodes(id) ON DELETE CASCADE,
    CONSTRAINT uq_watch_progress_user_profile_episode UNIQUE (user_id, profile_scope_id, episode_id),
    INDEX idx_watch_progress_user_updated (user_id, updated_at),
    INDEX idx_watch_progress_user_profile_updated (user_id, profile_scope_id, updated_at),
    INDEX idx_watch_progress_episode (episode_id)
);

CREATE TABLE IF NOT EXISTS movie_genre_terms (
    name VARCHAR(255) COLLATE utf8mb4_bin PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS catalog_series_genres (
    series_id VARCHAR(100) NOT NULL,
    genre_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (series_id, genre_id),
    INDEX idx_cat_series_genre (genre_id),
    CONSTRAINT fk_catalog_series_genres_series_id FOREIGN KEY (series_id) REFERENCES catalog_series(id) ON DELETE CASCADE,
    CONSTRAINT fk_catalog_series_genres_genre_id FOREIGN KEY (genre_id) REFERENCES catalog_genres(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS catalog_series_characters (
    series_id VARCHAR(100) NOT NULL,
    character_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (series_id, character_id),
    INDEX idx_cat_series_character (character_id),
    CONSTRAINT fk_catalog_series_characters_series_id FOREIGN KEY (series_id) REFERENCES catalog_series(id) ON DELETE CASCADE,
    CONSTRAINT fk_catalog_series_characters_character_id FOREIGN KEY (character_id) REFERENCES catalog_characters(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS catalog_character_series (
    character_id VARCHAR(100) NOT NULL,
    series_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (character_id, series_id),
    INDEX idx_cat_character_series (series_id),
    CONSTRAINT fk_catalog_character_series_character_id FOREIGN KEY (character_id) REFERENCES catalog_characters(id) ON DELETE CASCADE,
    CONSTRAINT fk_catalog_character_series_series_id FOREIGN KEY (series_id) REFERENCES catalog_series(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS movie_genres (
    movie_id VARCHAR(100) NOT NULL,
    genre_name VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
    PRIMARY KEY (movie_id, genre_name),
    INDEX idx_movie_genre_name (genre_name),
    CONSTRAINT fk_movie_genres_movie_id FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    CONSTRAINT fk_movie_genres_genre_name FOREIGN KEY (genre_name) REFERENCES movie_genre_terms(name) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_name VARCHAR(64) NOT NULL,
    record_id VARCHAR(100) NOT NULL,
    operation VARCHAR(8) NOT NULL,
    old_values JSON NOT NULL,
    new_values JSON,
    action_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mysql_user VARCHAR(288) NOT NULL,
    INDEX idx_audit_record (table_name, record_id, action_at)
);

CREATE TABLE IF NOT EXISTS admin_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT NULL,
    actor_username VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(100) NOT NULL,
    details_json LONGTEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_admin_audit_created (created_at),
    INDEX idx_admin_audit_target (target_type, target_id),
    CONSTRAINT fk_admin_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS playback_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    episode_id VARCHAR(100) NOT NULL,
    asset_id VARCHAR(100) NULL,
    reporter_user_id BIGINT NULL,
    reason VARCHAR(40) NOT NULL,
    note VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'open',
    resolved_by_user_id BIGINT NULL,
    resolved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_playback_reports_status_created (status, created_at),
    INDEX idx_playback_reports_episode (episode_id),
    CONSTRAINT fk_playback_reports_reporter FOREIGN KEY (reporter_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_playback_reports_resolver FOREIGN KEY (resolved_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT ck_playback_report_reason CHECK (HEX(reason) IN (HEX('khong_phat'), HEX('sai_tap'), HEX('am_thanh'), HEX('noi_dung_khong_phu_hop'), HEX('khac'))),
    CONSTRAINT ck_playback_report_status CHECK (HEX(status) IN (HEX('open'), HEX('resolved'), HEX('dismissed')))
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash),
    INDEX idx_password_reset_user (user_id),
    INDEX idx_password_reset_expires (expires_at),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS child_vip_series (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL,
    series_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_child_vip_series UNIQUE (profile_id, series_id),
    INDEX idx_child_vip_series_profile (profile_id),
    CONSTRAINT fk_child_vip_series_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_child_vip_series_series FOREIGN KEY (series_id) REFERENCES catalog_series(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS child_vip_episode_unlocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL,
    episode_id VARCHAR(100) NOT NULL,
    unlock_day DATE NOT NULL,
    unlocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_child_vip_unlock UNIQUE (profile_id, episode_id, unlock_day),
    INDEX idx_child_vip_unlock_profile_day (profile_id, unlock_day),
    CONSTRAINT fk_child_vip_unlock_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_child_vip_unlock_episode FOREIGN KEY (episode_id) REFERENCES catalog_episodes(id) ON DELETE CASCADE
);
