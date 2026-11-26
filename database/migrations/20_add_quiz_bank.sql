-- Chuyển cấp độ toán cũ sang khối lớp học và bổ sung ngân hàng câu hỏi trắc nghiệm, bộ câu hỏi, cấu hình theo bé và lịch sử trả lời.

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'child_profiles'
          AND column_name = 'quiz_grade'
    ),
    'ALTER TABLE child_profiles ADD COLUMN quiz_grade VARCHAR(20) NOT NULL DEFAULT ''LOP_1'' AFTER daily_minutes',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'child_profiles'
          AND column_name = 'math_level'
    ),
    'UPDATE child_profiles SET quiz_grade = CASE math_level WHEN 1 THEN ''MAU_GIAO'' WHEN 2 THEN ''LOP_1'' WHEN 3 THEN ''LOP_2'' WHEN 4 THEN ''LOP_3'' ELSE ''LOP_1'' END',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'child_profiles'
          AND constraint_name = 'ck_child_profile_math_level'
    ),
    'ALTER TABLE child_profiles DROP CHECK ck_child_profile_math_level',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'child_profiles'
          AND column_name = 'math_level'
    ),
    'ALTER TABLE child_profiles DROP COLUMN math_level',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = DATABASE()
          AND table_name = 'child_profiles'
          AND constraint_name = 'ck_child_profile_quiz_grade'
    ),
    'ALTER TABLE child_profiles ADD CONSTRAINT ck_child_profile_quiz_grade CHECK (HEX(quiz_grade) IN (HEX(''MAU_GIAO''), HEX(''LOP_1''), HEX(''LOP_2''), HEX(''LOP_3''), HEX(''LOP_4''), HEX(''LOP_5'')))',
    'DO 0'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS quiz_sets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_user_id BIGINT NULL,
    system_key VARCHAR(40) NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(300) NULL,
    default_subject VARCHAR(20) NOT NULL DEFAULT 'KHAC',
    default_grade VARCHAR(20) NULL,
    built_in BOOLEAN NOT NULL DEFAULT FALSE,
    dynamic_generator BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_quiz_sets_system_key UNIQUE (system_key),
    INDEX idx_quiz_sets_owner_active (owner_user_id, active),
    INDEX idx_quiz_sets_built_in (built_in, active),
    CONSTRAINT fk_quiz_sets_owner FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_quiz_sets_subject CHECK (HEX(default_subject) IN (HEX('TOAN'), HEX('TIENG_VIET'), HEX('TIENG_ANH'), HEX('KHOA_HOC'), HEX('DO_VUI'), HEX('KHAC'))),
    CONSTRAINT ck_quiz_sets_grade CHECK (default_grade IS NULL OR HEX(default_grade) IN (HEX('MAU_GIAO'), HEX('LOP_1'), HEX('LOP_2'), HEX('LOP_3'), HEX('LOP_4'), HEX('LOP_5')))
);

CREATE TABLE IF NOT EXISTS quiz_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    set_id BIGINT NOT NULL,
    seed_key VARCHAR(64) NULL,
    question_text VARCHAR(400) NOT NULL,
    option_a VARCHAR(200) NOT NULL,
    option_b VARCHAR(200) NOT NULL,
    option_c VARCHAR(200) NOT NULL,
    option_d VARCHAR(200) NOT NULL,
    correct_option VARCHAR(1) NOT NULL,
    explanation VARCHAR(400) NULL,
    subject VARCHAR(20) NOT NULL,
    grade VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_quiz_questions_seed_key UNIQUE (seed_key),
    INDEX idx_quiz_questions_set_active_grade (set_id, active, grade),
    CONSTRAINT fk_quiz_questions_set FOREIGN KEY (set_id) REFERENCES quiz_sets(id) ON DELETE CASCADE,
    CONSTRAINT ck_quiz_questions_correct CHECK (HEX(correct_option) IN (HEX('A'), HEX('B'), HEX('C'), HEX('D'))),
    CONSTRAINT ck_quiz_questions_subject CHECK (HEX(subject) IN (HEX('TOAN'), HEX('TIENG_VIET'), HEX('TIENG_ANH'), HEX('KHOA_HOC'), HEX('DO_VUI'), HEX('KHAC'))),
    CONSTRAINT ck_quiz_questions_grade CHECK (HEX(grade) IN (HEX('MAU_GIAO'), HEX('LOP_1'), HEX('LOP_2'), HEX('LOP_3'), HEX('LOP_4'), HEX('LOP_5')))
);

CREATE TABLE IF NOT EXISTS child_quiz_sets (
    profile_id BIGINT NOT NULL,
    set_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (profile_id, set_id),
    INDEX idx_child_quiz_sets_set (set_id),
    CONSTRAINT fk_child_quiz_sets_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_child_quiz_sets_set FOREIGN KEY (set_id) REFERENCES quiz_sets(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS quiz_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id BIGINT NOT NULL,
    set_id BIGINT NULL,
    question_id BIGINT NULL,
    question_key VARCHAR(120) NOT NULL,
    question_text VARCHAR(400) NOT NULL,
    set_name VARCHAR(100) NOT NULL,
    subject VARCHAR(20) NOT NULL,
    grade VARCHAR(20) NOT NULL,
    context VARCHAR(30) NOT NULL,
    correct BOOLEAN NOT NULL,
    response_ms INT NOT NULL DEFAULT 0,
    answered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_quiz_attempts_profile_answered (profile_id, answered_at),
    INDEX idx_quiz_attempts_profile_set_answered (profile_id, set_id, answered_at),
    CONSTRAINT fk_quiz_attempts_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_attempts_set FOREIGN KEY (set_id) REFERENCES quiz_sets(id) ON DELETE SET NULL,
    CONSTRAINT fk_quiz_attempts_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE SET NULL,
    CONSTRAINT ck_quiz_attempts_subject CHECK (HEX(subject) IN (HEX('TOAN'), HEX('TIENG_VIET'), HEX('TIENG_ANH'), HEX('KHOA_HOC'), HEX('DO_VUI'), HEX('KHAC'))),
    CONSTRAINT ck_quiz_attempts_grade CHECK (HEX(grade) IN (HEX('MAU_GIAO'), HEX('LOP_1'), HEX('LOP_2'), HEX('LOP_3'), HEX('LOP_4'), HEX('LOP_5'))),
    CONSTRAINT ck_quiz_attempts_context CHECK (HEX(context) IN (HEX('SCREEN_TIME'), HEX('WATCH_PET_MEDICINE'), HEX('LOOT_DROP'), HEX('VIP_UNLOCK'))),
    CONSTRAINT ck_quiz_attempts_response_ms CHECK (response_ms >= 0)
);
