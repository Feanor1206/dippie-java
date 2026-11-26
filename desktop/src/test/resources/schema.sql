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
    daily_minutes INT DEFAULT 60 CHECK (daily_minutes IS NULL OR daily_minutes BETWEEN 0 AND 600),
    quiz_grade VARCHAR(20) NOT NULL DEFAULT 'LOP_1' CHECK (quiz_grade IN ('MAU_GIAO', 'LOP_1', 'LOP_2', 'LOP_3', 'LOP_4', 'LOP_5')),
    reward_minutes INT NOT NULL DEFAULT 5 CHECK (reward_minutes BETWEEN 1 AND 30),
    max_bonus_minutes INT NOT NULL DEFAULT 30 CHECK (max_bonus_minutes BETWEEN 0 AND 120),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE screen_time_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
    usage_day DATE NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('WATCH', 'REWARD', 'PARENT_GRANT', 'BOUNTY', 'LOOT')),
    seconds INT NOT NULL CHECK (seconds > 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE bounty_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    reward_minutes INT NOT NULL CHECK (reward_minutes BETWEEN 1 AND 120),
    icon VARCHAR(32),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    repeatable_daily BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE bounty_task_profiles (
    task_id BIGINT NOT NULL REFERENCES bounty_tasks(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
    PRIMARY KEY (task_id, profile_id)
);

CREATE TABLE bounty_submissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES bounty_tasks(id) ON DELETE CASCADE,
    profile_id BIGINT NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
    usage_day DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reward_minutes INT NOT NULL CHECK (reward_minutes BETWEEN 1 AND 120),
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at TIMESTAMP NULL,
    reject_note VARCHAR(255)
);

CREATE TABLE weekend_vault_overrides (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
    week_start DATE NOT NULL,
    unlocked BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE (profile_id, week_start)
);

CREATE TABLE child_play_settings (
    profile_id BIGINT PRIMARY KEY REFERENCES child_profiles(id) ON DELETE CASCADE,
    watch_pet_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    watch_pet_faint_minutes INT NOT NULL DEFAULT 20 CHECK (watch_pet_faint_minutes BETWEEN 10 AND 60),
    watch_pet_rest_minutes INT NOT NULL DEFAULT 5 CHECK (watch_pet_rest_minutes BETWEEN 3 AND 15),
    loot_drop_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    recap_to_next_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE child_play_state (
    profile_id BIGINT PRIMARY KEY REFERENCES child_profiles(id) ON DELETE CASCADE,
    continuous_watch_seconds INT NOT NULL DEFAULT 0 CHECK (continuous_watch_seconds >= 0),
    last_watch_heartbeat_at TIMESTAMP NULL,
    pet_fainted BOOLEAN NOT NULL DEFAULT FALSE,
    rest_lock_until TIMESTAMP NULL,
    watch_seconds_since_loot INT NOT NULL DEFAULT 0 CHECK (watch_seconds_since_loot >= 0),
    next_loot_threshold_seconds INT NOT NULL DEFAULT 360 CHECK (next_loot_threshold_seconds BETWEEN 300 AND 420),
    loot_combo INT NOT NULL DEFAULT 0 CHECK (loot_combo >= 0),
    loot_drop_pending BOOLEAN NOT NULL DEFAULT FALSE,
    last_loot_issued_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE child_episode_recap_gates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
    series_id VARCHAR(100) NOT NULL,
    source_episode_id VARCHAR(100) NOT NULL,
    target_episode_id VARCHAR(100) NOT NULL,
    unlocked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unlocked_at TIMESTAMP NULL,
    UNIQUE (profile_id, target_episode_id)
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

CREATE TABLE child_vip_series (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
    series_id VARCHAR(100) NOT NULL REFERENCES catalog_series(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (profile_id, series_id)
);

CREATE TABLE child_vip_episode_unlocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
    episode_id VARCHAR(100) NOT NULL REFERENCES catalog_episodes(id) ON DELETE CASCADE,
    unlock_day DATE NOT NULL,
    unlocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (profile_id, episode_id, unlock_day)
);

CREATE TABLE quiz_sets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_user_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    system_key VARCHAR(40) UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(300),
    default_subject VARCHAR(20) NOT NULL DEFAULT 'KHAC' CHECK (default_subject IN ('TOAN', 'TIENG_VIET', 'TIENG_ANH', 'KHOA_HOC', 'DO_VUI', 'KHAC')),
    default_grade VARCHAR(20) CHECK (default_grade IS NULL OR default_grade IN ('MAU_GIAO', 'LOP_1', 'LOP_2', 'LOP_3', 'LOP_4', 'LOP_5')),
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    dynamic_generator BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE quiz_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    set_id BIGINT NOT NULL REFERENCES quiz_sets(id) ON DELETE CASCADE,
    seed_key VARCHAR(64) UNIQUE,
    question_text VARCHAR(400) NOT NULL,
    option_a VARCHAR(200) NOT NULL,
    option_b VARCHAR(200) NOT NULL,
    option_c VARCHAR(200) NOT NULL,
    option_d VARCHAR(200) NOT NULL,
    correct_option VARCHAR(1) NOT NULL CHECK (correct_option IN ('A', 'B', 'C', 'D')),
    explanation VARCHAR(400),
    subject VARCHAR(20) NOT NULL CHECK (subject IN ('TOAN', 'TIENG_VIET', 'TIENG_ANH', 'KHOA_HOC', 'DO_VUI', 'KHAC')),
    grade VARCHAR(20) NOT NULL CHECK (grade IN ('MAU_GIAO', 'LOP_1', 'LOP_2', 'LOP_3', 'LOP_4', 'LOP_5')),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE child_quiz_sets (
    profile_id BIGINT NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
    set_id BIGINT NOT NULL REFERENCES quiz_sets(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (profile_id, set_id)
);

CREATE TABLE quiz_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL REFERENCES child_profiles(id) ON DELETE CASCADE,
    set_id BIGINT REFERENCES quiz_sets(id) ON DELETE SET NULL,
    question_id BIGINT REFERENCES quiz_questions(id) ON DELETE SET NULL,
    question_key VARCHAR(120) NOT NULL,
    question_text VARCHAR(400) NOT NULL,
    set_name VARCHAR(100) NOT NULL,
    subject VARCHAR(20) NOT NULL CHECK (subject IN ('TOAN', 'TIENG_VIET', 'TIENG_ANH', 'KHOA_HOC', 'DO_VUI', 'KHAC')),
    grade VARCHAR(20) NOT NULL CHECK (grade IN ('MAU_GIAO', 'LOP_1', 'LOP_2', 'LOP_3', 'LOP_4', 'LOP_5')),
    context VARCHAR(30) NOT NULL CHECK (context IN ('SCREEN_TIME', 'WATCH_PET_MEDICINE', 'LOOT_DROP', 'VIP_UNLOCK')),
    correct BOOLEAN NOT NULL,
    response_ms INT NOT NULL DEFAULT 0 CHECK (response_ms >= 0),
    answered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
