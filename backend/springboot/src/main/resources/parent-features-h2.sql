ALTER TABLE IF EXISTS child_profiles ALTER COLUMN daily_minutes SET NULL;
CREATE TABLE IF NOT EXISTS bounty_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    reward_minutes INT NOT NULL,
    icon VARCHAR(32),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    repeatable_daily BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bounty_tasks_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_bounty_tasks_user_active ON bounty_tasks (user_id, active);
CREATE TABLE IF NOT EXISTS bounty_task_profiles (
    task_id BIGINT NOT NULL,
    profile_id BIGINT NOT NULL,
    PRIMARY KEY (task_id, profile_id),
    CONSTRAINT fk_bounty_task_profiles_task FOREIGN KEY (task_id) REFERENCES bounty_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_bounty_task_profiles_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_bounty_task_profiles_profile ON bounty_task_profiles (profile_id);
CREATE TABLE IF NOT EXISTS bounty_submissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    profile_id BIGINT NOT NULL,
    usage_day DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    reward_minutes INT NOT NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at TIMESTAMP,
    reject_note VARCHAR(255),
    CONSTRAINT fk_bounty_submissions_task FOREIGN KEY (task_id) REFERENCES bounty_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_bounty_submissions_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_bounty_submissions_profile_day ON bounty_submissions (profile_id, usage_day, status);
CREATE INDEX IF NOT EXISTS idx_bounty_submissions_task_profile_status ON bounty_submissions (task_id, profile_id, status);
CREATE TABLE IF NOT EXISTS weekend_vault_overrides (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL,
    week_start DATE NOT NULL,
    unlocked BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_weekend_vault_profile_week UNIQUE (profile_id, week_start),
    CONSTRAINT fk_weekend_vault_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS child_vip_series (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL,
    series_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_child_vip_series UNIQUE (profile_id, series_id),
    CONSTRAINT fk_child_vip_series_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_child_vip_series_series FOREIGN KEY (series_id) REFERENCES catalog_series(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_child_vip_series_profile ON child_vip_series (profile_id);
CREATE TABLE IF NOT EXISTS child_vip_episode_unlocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL,
    episode_id VARCHAR(100) NOT NULL,
    unlock_day DATE NOT NULL,
    unlocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_child_vip_unlock UNIQUE (profile_id, episode_id, unlock_day),
    CONSTRAINT fk_child_vip_unlock_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_child_vip_unlock_episode FOREIGN KEY (episode_id) REFERENCES catalog_episodes(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_child_vip_unlock_profile_day ON child_vip_episode_unlocks (profile_id, unlock_day);
