SET @account_ddl = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'users'
              AND column_name = 'enabled'),
    'ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER role',
    'DO 0'
);
PREPARE account_ddl FROM @account_ddl;
EXECUTE account_ddl;
DEALLOCATE PREPARE account_ddl;

SET @account_ddl = IF(
    NOT EXISTS (SELECT 1 FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'users'
              AND column_name = 'token_version'),
    'ALTER TABLE users ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0 AFTER enabled',
    'DO 0'
);
PREPARE account_ddl FROM @account_ddl;
EXECUTE account_ddl;
DEALLOCATE PREPARE account_ddl;

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
