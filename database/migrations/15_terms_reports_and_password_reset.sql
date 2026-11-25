-- Bản nâng cấp thủ công đồng bộ với Flyway V2, V3 và V4 (điều khoản đăng ký, báo cáo lỗi phát và đặt lại mật khẩu).

SET @terms_ddl = IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'users'
          AND column_name = 'terms_accepted_at'
    ),
    'ALTER TABLE users ADD COLUMN terms_accepted_at TIMESTAMP NULL AFTER token_version',
    'DO 0'
);
PREPARE terms_ddl FROM @terms_ddl;
EXECUTE terms_ddl;
DEALLOCATE PREPARE terms_ddl;

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
