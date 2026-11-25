-- Bảng lưu báo cáo sự cố phát video từ khán giả và trạng thái xử lý của quản trị viên.

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
