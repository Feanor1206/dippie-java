-- Bổ sung thời điểm người dùng đồng ý điều khoản khi đăng ký tài khoản (lũy đẳng cho cơ sở dữ liệu đã cập nhật).

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
