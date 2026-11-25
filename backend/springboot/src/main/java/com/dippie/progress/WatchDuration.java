package com.dippie.progress;

// Gói thời lượng hiệu dụng của một tập phim để kiểm tra vượt giới hạn thời gian và ngưỡng xem xong.
public record WatchDuration(Long durationMs) {

    // Lấy giá trị dương nhỏ hơn giữa thời lượng ghi ở tập và thời lượng file thật để tránh trường hợp file ngắn hơn metadata làm người xem không bao giờ chạm ngưỡng hoàn thành.
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
