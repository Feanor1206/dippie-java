package com.dippie.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

// đếm rate limit theo cửa sổ cố định trong RAM, Việt để ý chỗ đẩy phần tử khi bảng đầy nha
// chạy kiểu fail-closed, không đẩy mấy key đang bị chặn ra kẻo bị spam key rác xóa mất bộ đếm
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

    // cộng 1 lượt gọi cho key, còn hạn mức thì trả 0, chạm trần thì trả số giây cần chờ
    public long retryAfterSeconds(String key) {
        String normalizedKey = (key == null || key.isBlank()) ? "unknown" : key;
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        synchronized (lock) {
            // dọn mấy cửa sổ hết hạn trước khi check hoặc thêm key mới
            evictExpired(cutoff);
            WindowState current = entries.get(normalizedKey);
            if (current != null && current.count() >= maxAttempts) {
                return secondsUntilReset(now, current.startedAt());
            }
            if (current == null) {
                if (entries.size() >= maxEntries && !evictLowestUnblocked()) {
                    // bảng chật kín key đang bị chặn thì từ chối luôn request mới chứ không đá mục tiêu bị khóa ra
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

    // bảng đầy thì chỉ chọn key chưa bị chặn và ít lượt thử nhất để nhường chỗ, giữ nguyên mấy key đang bị khóa
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

    // giữ mốc mở cửa sổ và số lượt thử đã đếm cho 1 key
    private record WindowState(Instant startedAt, int count) {

        private WindowState increment() {
            return new WindowState(startedAt, count + 1);
        }
    }
}
