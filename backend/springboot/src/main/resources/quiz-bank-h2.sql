ALTER TABLE IF EXISTS child_profiles ADD COLUMN IF NOT EXISTS quiz_grade VARCHAR(20);
ALTER TABLE IF EXISTS child_profiles ADD COLUMN IF NOT EXISTS math_level INT;
UPDATE child_profiles
SET quiz_grade = CASE math_level
    WHEN 1 THEN 'MAU_GIAO'
    WHEN 2 THEN 'LOP_1'
    WHEN 3 THEN 'LOP_2'
    WHEN 4 THEN 'LOP_3'
    ELSE 'LOP_1'
END
WHERE quiz_grade IS NULL;
ALTER TABLE IF EXISTS child_profiles ALTER COLUMN quiz_grade SET DEFAULT 'LOP_1';
ALTER TABLE IF EXISTS child_profiles ALTER COLUMN quiz_grade SET NOT NULL;
ALTER TABLE IF EXISTS child_profiles DROP COLUMN IF EXISTS math_level;

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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_quiz_sets_system_key UNIQUE (system_key),
    CONSTRAINT fk_quiz_sets_owner FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_quiz_sets_owner_active ON quiz_sets (owner_user_id, active);
CREATE INDEX IF NOT EXISTS idx_quiz_sets_built_in ON quiz_sets (built_in, active);

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
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_quiz_questions_seed_key UNIQUE (seed_key),
    CONSTRAINT fk_quiz_questions_set FOREIGN KEY (set_id) REFERENCES quiz_sets(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_quiz_questions_set_active_grade ON quiz_questions (set_id, active, grade);

CREATE TABLE IF NOT EXISTS child_quiz_sets (
    profile_id BIGINT NOT NULL,
    set_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (profile_id, set_id),
    CONSTRAINT fk_child_quiz_sets_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_child_quiz_sets_set FOREIGN KEY (set_id) REFERENCES quiz_sets(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_child_quiz_sets_set ON child_quiz_sets (set_id);

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
    CONSTRAINT fk_quiz_attempts_profile FOREIGN KEY (profile_id) REFERENCES child_profiles(id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_attempts_set FOREIGN KEY (set_id) REFERENCES quiz_sets(id) ON DELETE SET NULL,
    CONSTRAINT fk_quiz_attempts_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_quiz_attempts_profile_answered ON quiz_attempts (profile_id, answered_at);
CREATE INDEX IF NOT EXISTS idx_quiz_attempts_profile_set_answered ON quiz_attempts (profile_id, set_id, answered_at);
