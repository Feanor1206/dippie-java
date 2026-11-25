package com.dippie.catalog.domain;

// Chuẩn hóa thời lượng tập phim và đổi sang nhãn số phút; tập ngắn dưới 1 phút bị coi là chưa rõ thời lượng để lọc rác dữ liệu.
public final class EpisodeRuntime {

    public static final long MINUTE_MS = 60_000L;
    public static final long SHORT_MAX_MS = 10 * MINUTE_MS;
    public static final long HOUR_MS = 60 * MINUTE_MS;

    private EpisodeRuntime() {
    }

    // Chỉ giữ thời lượng từ 1 phút trở lên, nhỏ hơn hoặc null thì trả null.
    public static Long known(Long durationMs) {
        return durationMs != null && durationMs >= MINUTE_MS ? durationMs : null;
    }

    // So sánh thời lượng hợp lệ với ngưỡng tối đa; tập chưa rõ thời lượng luôn trả false.
    public static boolean atMost(Long durationMs, long maxDurationMs) {
        Long duration = known(durationMs);
        return duration != null && duration <= maxDurationMs;
    }

    // Hiển thị dạng "<số phút>m", còn thiếu thời lượng thì rơi về nhãn "Tập phim".
    public static String label(Long durationMs) {
        Long duration = known(durationMs);
        return duration == null ? CatalogDisplayText.EPISODE : duration / MINUTE_MS + "m";
    }
}
