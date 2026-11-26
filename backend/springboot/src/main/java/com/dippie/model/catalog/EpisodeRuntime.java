package com.dippie.model.catalog;


// chuẩn hóa thời lượng với nhãn số phút, tập dưới 1 phút coi như chưa rõ để lọc dữ liệu rác
public final class EpisodeRuntime {

    public static final long MINUTE_MS = 60_000L;
    public static final long SHORT_MAX_MS = 10 * MINUTE_MS;
    public static final long HOUR_MS = 60 * MINUTE_MS;

    private EpisodeRuntime() {
    }

    // từ 1 phút trở lên mới giữ, nhỏ hơn hay null thì trả null
    public static Long known(Long durationMs) {
        return durationMs != null && durationMs >= MINUTE_MS ? durationMs : null;
    }

    // check thời lượng không vượt trần, tập chưa rõ thời lượng thì luôn false
    public static boolean atMost(Long durationMs, long maxDurationMs) {
        Long duration = known(durationMs);
        return duration != null && duration <= maxDurationMs;
    }

    // ra chuỗi "<số phút>m", thiếu thời lượng thì rơi về nhãn "Tập phim"
    public static String label(Long durationMs) {
        Long duration = known(durationMs);
        return duration == null ? CatalogDisplayText.EPISODE : duration / MINUTE_MS + "m";
    }
}
