package com.dippie.progress;

// Quy tắc xác định xem một tập phim đã được coi là xem xong chưa.
// Việt nhớ đừng tự sửa NEAR_END_CAP_MS hay COMPLETION_DIVISOR nhé: AdminStatsQuery dùng thẳng hai hằng này trong JPQL để số thống kê khớp y hệt bên player.
public final class CompletionPolicy {

    public static final long NEAR_END_CAP_MS = 60_000L;
    public static final long COMPLETION_DIVISOR = 20L;

    private CompletionPolicy() {
    }

    // Coi là xem xong khi người xem đã vào 5% cuối (tối thiểu 1 ms cho clip siêu ngắn), nhưng chặn trần ở 60 giây cuối với tập dài.
    public static boolean isCompleted(long positionMs, Long durationMs) {
        if (durationMs == null || durationMs <= 0) {
            return false;
        }
        long fivePercent = Math.max(1L, durationMs / COMPLETION_DIVISOR);
        long nearEnd = Math.min(NEAR_END_CAP_MS, fivePercent);
        return positionMs >= durationMs - nearEnd;
    }
}
