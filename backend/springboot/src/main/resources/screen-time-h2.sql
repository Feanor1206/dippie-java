ALTER TABLE IF EXISTS child_profiles ADD COLUMN IF NOT EXISTS daily_minutes INT NOT NULL DEFAULT 60;
ALTER TABLE IF EXISTS child_profiles ADD COLUMN IF NOT EXISTS math_level INT NOT NULL DEFAULT 1;
ALTER TABLE IF EXISTS child_profiles ADD COLUMN IF NOT EXISTS reward_minutes INT NOT NULL DEFAULT 5;
ALTER TABLE IF EXISTS child_profiles ADD COLUMN IF NOT EXISTS max_bonus_minutes INT NOT NULL DEFAULT 30;
CREATE TABLE IF NOT EXISTS screen_time_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL,
    usage_day DATE NOT NULL,
    kind VARCHAR(20) NOT NULL,
    seconds INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_screen_time_ledger_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_screen_time_ledger_profile_day ON screen_time_ledger (profile_id, usage_day, kind);
CREATE INDEX IF NOT EXISTS idx_screen_time_ledger_profile_kind_created ON screen_time_ledger (profile_id, kind, created_at);
