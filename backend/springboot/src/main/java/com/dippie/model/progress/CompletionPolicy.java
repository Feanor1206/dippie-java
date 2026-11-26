package com.dippie.model.progress;

// luật tính xem tập phim đã xem xong chưa
// Việt đừng tự sửa NEAR_END_CAP_MS hay COMPLETION_DIVISOR nha, AdminStatsQuery kéo thẳng vào JPQL để số liệu khớp y hệt bên player
public final class CompletionPolicy {

    public static final long NEAR_END_CAP_MS = 60_000L;
    public static final long COMPLETION_DIVISOR = 20L;

    private CompletionPolicy() {
    }

    // vào 5% cuối là tính xem xong (ít nhất 1 ms với clip siêu ngắn), còn tập dài thì chặn trần ở 60 giây cuối
    public static boolean isCompleted(long positionMs, Long durationMs) {
        if (durationMs == null || durationMs <= 0) {
            return false;
        }
        long fivePercent = Math.max(1L, durationMs / COMPLETION_DIVISOR);
        long nearEnd = Math.min(NEAR_END_CAP_MS, fivePercent);
        return positionMs >= durationMs - nearEnd;
    }
}
