package com.dippie.progress;

public final class CompletionPolicy {

    private static final long NEAR_END_CAP_MS = 60_000L;
    private static final long COMPLETION_DIVISOR = 20L;

    private CompletionPolicy() {
    }

    public static boolean isCompleted(long positionMs, Long durationMs) {
        if (durationMs == null || durationMs <= 0) {
            return false;
        }
        long fivePercent = Math.max(1L, durationMs / COMPLETION_DIVISOR);
        long nearEnd = Math.min(NEAR_END_CAP_MS, fivePercent);
        long threshold = Math.max(durationMs - fivePercent, durationMs - nearEnd);
        return positionMs >= threshold;
    }
}
