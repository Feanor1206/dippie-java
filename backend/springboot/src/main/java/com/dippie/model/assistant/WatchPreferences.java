package com.dippie.model.assistant;

import com.dippie.model.catalog.EpisodeMood;
import com.dippie.model.catalog.EpisodeRuntime;

import java.util.regex.Pattern;

// trần thời lượng với cờ ưu tiên tập ngắn bóc ra từ câu hỏi (dưới X phút, trước khi ngủ hay cho bé)
public record WatchPreferences(Long maxDurationMs, boolean preferShort) {

    private static final Pattern DURATION = Pattern.compile("\\b(duoi|it hon|toi da|khong qua)\\s+(\\d{1,3})\\s*(?:phut|min)\\b");

    // user nói rõ số phút thì lấy số đó ("dưới"/"ít hơn" trừ 1 ms thành nhỏ hơn hẳn), không thì lấy trần mặc định theo ngữ cảnh
    public static WatchPreferences from(String foldedQuery, String fullQuery) {
        Long defaultMax = defaultBound(fullQuery);
        var durationMatcher = DURATION.matcher(foldedQuery);
        if (!durationMatcher.find()) {
            durationMatcher = DURATION.matcher(fullQuery);
            if (!durationMatcher.find()) {
                return new WatchPreferences(defaultMax, defaultMax != null);
            }
        }
        long duration = Long.parseLong(durationMatcher.group(2)) * EpisodeRuntime.MINUTE_MS;
        boolean strict = "duoi".equals(durationMatcher.group(1)) || "it hon".equals(durationMatcher.group(1));
        duration = strict ? duration - 1 : duration;
        long effectiveMax = defaultMax == null ? duration : Math.min(duration, defaultMax);
        return new WatchPreferences(effectiveMax, true);
    }

    private static Long defaultBound(String query) {
        if (EpisodeMood.shortRequest(query) || EpisodeMood.bedtimeRequest(query)) {
            return EpisodeRuntime.SHORT_MAX_MS;
        }
        return EpisodeMood.childRequest(query) ? EpisodeRuntime.HOUR_MS : null;
    }
}
