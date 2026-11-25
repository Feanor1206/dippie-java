package com.dippie.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

// Bộ đếm rate limit theo cửa sổ cố định trong bộ nhớ. Việt để ý chỗ đẩy phần tử khi đầy bảng:
// mình làm theo kiểu fail-closed, không cho đẩy mấy key đang bị chặn ra ngoài kẻo attacker spam key rác để xóa bộ đếm.
public final class FixedWindowRateLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final int maxEntries;
    private final Clock clock;
    private final Map<String, WindowState> entries = new LinkedHashMap<>();
    private final Object lock = new Object();

    public FixedWindowRateLimiter(int maxAttempts, Duration window, int maxEntries, Clock clock) {
        if (maxAttempts <= 0 || window == null || window.isNegative() || window.isZero() || maxEntries <= 0) {
            throw new IllegalArgumentException("Cấu hình giới hạn tần suất chưa hợp lệ.");
        }
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    // Cộng một lượt gọi cho key; trả 0 nếu còn hạn mức, trả số giây cần chờ nếu đã chạm trần.
    public long retryAfterSeconds(String key) {
        String normalizedKey = (key == null || key.isBlank()) ? "unknown" : key;
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        synchronized (lock) {
            // Dọn các cửa sổ đã hết hạn trước khi kiểm tra hoặc thêm key mới.
            evictExpired(cutoff);
            WindowState current = entries.get(normalizedKey);
            if (current != null && current.count() >= maxAttempts) {
                return secondsUntilReset(now, current.startedAt());
            }
            if (current == null) {
                if (entries.size() >= maxEntries && !evictLowestUnblocked()) {
                    // Bảng đã chật toàn key đang bị chặn thì từ chối luôn request mới thay vì đá mục tiêu bị khóa ra.
                    return Math.max(1L, window.toSeconds());
                }
                entries.put(normalizedKey, new WindowState(now, 1));
            } else {
                entries.put(normalizedKey, current.increment());
            }
            return 0L;
        }
    }

    public void reset() {
        synchronized (lock) {
            entries.clear();
        }
    }

    public int trackedCount() {
        synchronized (lock) {
            return entries.size();
        }
    }

    private void evictExpired(Instant cutoff) {
        Iterator<Map.Entry<String, WindowState>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, WindowState> entry = iterator.next();
            if (!entry.getValue().startedAt().isAfter(cutoff)) {
                iterator.remove();
            } else {
                break;
            }
        }
    }

    // Khi bảng đầy, chỉ chọn key chưa bị chặn và có ít lượt thử nhất để nhường chỗ, giữ nguyên mấy key đang bị khóa.
    private boolean evictLowestUnblocked() {
        String victimKey = null;
        int lowestCount = Integer.MAX_VALUE;
        for (Map.Entry<String, WindowState> entry : entries.entrySet()) {
            int count = entry.getValue().count();
            if (count < maxAttempts && count < lowestCount) {
                lowestCount = count;
                victimKey = entry.getKey();
                if (count == 1) {
                    break;
                }
            }
        }
        if (victimKey == null) {
            return false;
        }
        entries.remove(victimKey);
        return true;
    }

    private long secondsUntilReset(Instant now, Instant startedAt) {
        long remainingMillis = Duration.between(now, startedAt.plus(window)).toMillis();
        long remainingSeconds = Math.floorDiv(remainingMillis, 1000L);
        return Math.max(1L, remainingSeconds + 1L);
    }

    // Lưu mốc mở cửa sổ và số lượt thử đã đếm cho một key.
    private record WindowState(Instant startedAt, int count) {

        private WindowState increment() {
            return new WindowState(startedAt, count + 1);
        }
    }
}
