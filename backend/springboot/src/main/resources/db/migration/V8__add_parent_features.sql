-- Bổ sung chế độ không giới hạn giờ xem, bảng nhiệm vụ đời thực (Bounty), két sắt phim cuối tuần và phim VIP theo hồ sơ trẻ em.

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'child_profiles' AND constraint_name = 'ck_child_profile_daily_minutes'
    ),
    'ALTER TABLE child_profiles DROP CHECK ck_child_profile_daily_minutes',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE child_profiles MODIFY COLUMN daily_minutes INT NULL DEFAULT 60;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'child_profiles' AND constraint_name = 'ck_child_profile_daily_minutes'
    ),
    'ALTER TABLE child_profiles ADD CONSTRAINT ck_child_profile_daily_minutes CHECK (daily_minutes IS NULL OR daily_minutes BETWEEN 0 AND 600)',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'screen_time_ledger' AND constraint_name = 'ck_screen_time_ledger_kind'
    ),
    'ALTER TABLE screen_time_ledger DROP CHECK ck_screen_time_ledger_kind',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'screen_time_ledger' AND constraint_name = 'ck_screen_time_ledger_kind'
    ),
    'ALTER TABLE screen_time_ledger ADD CONSTRAINT ck_screen_time_ledger_kind CHECK (HEX(kind) IN (HEX(''WATCH''), HEX(''REWARD''), HEX(''PARENT_GRANT''), HEX(''BOUNTY'')))',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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
