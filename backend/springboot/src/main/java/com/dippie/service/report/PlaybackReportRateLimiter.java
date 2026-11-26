package com.dippie.service.report;

import com.dippie.config.report.PlaybackReportRateLimitProperties;
import com.dippie.exception.TooManyRequestsException;
import com.dippie.security.FixedWindowRateLimiter;

import org.springframework.stereotype.Component;

import java.time.Clock;

// chặn spam báo lỗi theo IP bằng FixedWindowRateLimiter kẻo đầy bảng playback_reports
@Component
public class PlaybackReportRateLimiter {

    private final FixedWindowRateLimiter delegate;

    public PlaybackReportRateLimiter(PlaybackReportRateLimitProperties properties, Clock clock) {
        this.delegate = new FixedWindowRateLimiter(
                properties.maxAttempts(),
                properties.window(),
                properties.maxClients(),
                clock
        );
    }

    // IP vượt ngưỡng thì ném 429 kèm số giây chờ Retry-After
    public void enforce(String clientIp) {
        String key = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp.trim();
        long retryAfter = delegate.retryAfterSeconds("report:" + key);
        if (retryAfter > 0) {
            throw new TooManyRequestsException("Bạn đã gửi quá nhiều báo cáo trong thời gian ngắn. Vui lòng đợi một lát rồi thử lại.", retryAfter);
        }
    }

    // xóa bộ đếm giữa các bài test cho khỏi dính trạng thái
    public void reset() {
        delegate.reset();
    }
}
