package com.dippie.config.report;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// cấu hình rate limit gửi báo lỗi theo IP, mặc định 10 lần trong 10 phút và giữ tối đa 4096 IP
@ConfigurationProperties("dippie.report.rate-limit")
public record PlaybackReportRateLimitProperties(int maxAttempts, Duration window, int maxClients) {

    public PlaybackReportRateLimitProperties {
        if (maxAttempts <= 0) {
            maxAttempts = 10;
        }
        if (window == null || window.isNegative() || window.isZero()) {
            window = Duration.ofMinutes(10);
        }
        if (maxClients <= 0) {
            maxClients = 4096;
        }
    }
}
