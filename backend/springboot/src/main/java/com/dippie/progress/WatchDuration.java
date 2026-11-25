package com.dippie.progress;

public record WatchDuration(Long durationMs) {

    public static WatchDuration resolve(Long episodeDurationMs, Long assetDurationMs) {
        boolean episodeKnown = episodeDurationMs != null && episodeDurationMs > 0;
        boolean assetKnown = assetDurationMs != null && assetDurationMs > 0;
        if (episodeKnown && assetKnown) {
            return new WatchDuration(Math.min(episodeDurationMs, assetDurationMs));
        }
        if (episodeKnown) {
            return new WatchDuration(episodeDurationMs);
        }
        return new WatchDuration(assetKnown ? assetDurationMs : null);
    }

    private boolean isKnown() {
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
