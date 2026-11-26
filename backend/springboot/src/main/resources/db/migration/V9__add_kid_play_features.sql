-- Bổ sung tính năng xem phim cho trẻ em: thú ảo canh giờ nghỉ mắt, rương báu bay ngang (LOOT) và ôn tập qua tập.

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'screen_time_ledger'
          AND constraint_name = 'ck_screen_time_ledger_kind'
    ),
    'ALTER TABLE screen_time_ledger DROP CHECK ck_screen_time_ledger_kind',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'screen_time_ledger'
          AND constraint_name = 'ck_screen_time_ledger_kind'
    ),
    'ALTER TABLE screen_time_ledger ADD CONSTRAINT ck_screen_time_ledger_kind CHECK (HEX(kind) IN (HEX(''WATCH''), HEX(''REWARD''), HEX(''PARENT_GRANT''), HEX(''BOUNTY''), HEX(''LOOT'')))',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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
