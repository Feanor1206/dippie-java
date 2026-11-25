package com.dippie.progress;

import java.util.stream.Stream;

public record WatchDuration(Long durationMs) {

    public static WatchDuration resolve(Long episodeDurationMs, Long assetDurationMs) {
        Long resolved = Stream.of(episodeDurationMs, assetDurationMs)
                .filter(value -> value != null && value > 0)
                .min(Long::compareTo)
                .orElse(null);
        return new WatchDuration(resolved);
    }

    public boolean isKnown() {
        return durationMs != null && durationMs > 0;
    }

    public Long nullableDurationMs() {
        return isKnown() ? durationMs : null;
    }

    public boolean isExceededBy(long positionMs) {
        return isKnown() && positionMs > durationMs;
    }

    public boolean isCompletedAt(long positionMs) {
        return CompletionPolicy.isCompleted(positionMs, nullableDurationMs());
    }
}
