-- Bổ sung giới hạn thời gian xem, cấu hình giải toán theo hồ sơ trẻ em và sổ cái screen_time_ledger.

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'child_profiles' AND column_name = 'daily_minutes'
    ),
    'ALTER TABLE child_profiles ADD COLUMN daily_minutes INT NOT NULL DEFAULT 60 AFTER avatar_key',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'child_profiles' AND column_name = 'math_level'
    ),
    'ALTER TABLE child_profiles ADD COLUMN math_level INT NOT NULL DEFAULT 1 AFTER daily_minutes',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'child_profiles' AND column_name = 'reward_minutes'
    ),
    'ALTER TABLE child_profiles ADD COLUMN reward_minutes INT NOT NULL DEFAULT 5 AFTER math_level',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'child_profiles' AND column_name = 'max_bonus_minutes'
    ),
    'ALTER TABLE child_profiles ADD COLUMN max_bonus_minutes INT NOT NULL DEFAULT 30 AFTER reward_minutes',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'child_profiles' AND constraint_name = 'ck_child_profile_daily_minutes'
    ),
    'ALTER TABLE child_profiles ADD CONSTRAINT ck_child_profile_daily_minutes CHECK (daily_minutes BETWEEN 0 AND 600)',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'child_profiles' AND constraint_name = 'ck_child_profile_math_level'
    ),
    'ALTER TABLE child_profiles ADD CONSTRAINT ck_child_profile_math_level CHECK (math_level BETWEEN 1 AND 4)',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'child_profiles' AND constraint_name = 'ck_child_profile_reward_minutes'
    ),
    'ALTER TABLE child_profiles ADD CONSTRAINT ck_child_profile_reward_minutes CHECK (reward_minutes BETWEEN 1 AND 30)',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE() AND table_name = 'child_profiles' AND constraint_name = 'ck_child_profile_max_bonus_minutes'
    ),
    'ALTER TABLE child_profiles ADD CONSTRAINT ck_child_profile_max_bonus_minutes CHECK (max_bonus_minutes BETWEEN 0 AND 120)',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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
    CONSTRAINT ck_screen_time_ledger_kind CHECK (HEX(kind) IN (HEX('WATCH'), HEX('REWARD'), HEX('PARENT_GRANT'))),
    CONSTRAINT ck_screen_time_ledger_seconds CHECK (seconds > 0)
);
