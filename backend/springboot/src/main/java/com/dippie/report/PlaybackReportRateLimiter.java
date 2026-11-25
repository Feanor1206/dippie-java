package com.dippie.report;

import com.dippie.common.error.TooManyRequestsException;
import com.dippie.security.FixedWindowRateLimiter;
import java.time.Clock;
import org.springframework.stereotype.Component;

// Giới hạn tần suất gửi báo lỗi theo IP bằng FixedWindowRateLimiter để tránh spam làm đầy bảng playback_reports.
@Component
public class PlaybackReportRateLimiter {

    private static final String LIMIT_EXCEEDED_MESSAGE =
            "Bạn đã gửi quá nhiều báo cáo trong thời gian ngắn. Vui lòng đợi một lát rồi thử lại.";

    private final FixedWindowRateLimiter delegate;

    public PlaybackReportRateLimiter(PlaybackReportRateLimitProperties properties, Clock clock) {
        this.delegate = new FixedWindowRateLimiter(
                properties.maxAttempts(),
                properties.window(),
                properties.maxClients(),
                clock
        );
    }

    // Chặn IP vượt ngưỡng bằng lỗi 429 kèm thời gian chờ Retry-After.
    public void enforce(String clientIp) {
        String key = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp.trim();
        long retryAfter = delegate.retryAfterSeconds("report:" + key);
        if (retryAfter > 0) {
            throw new TooManyRequestsException(LIMIT_EXCEEDED_MESSAGE, retryAfter);
        }
    }

    // Xóa bộ đếm giữa các bài test để trạng thái không bị dính sang nhau.
    public void reset() {
        delegate.reset();
    }
}
