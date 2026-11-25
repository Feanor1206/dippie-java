package com.dippie.recommendation;

import com.dippie.catalog.domain.EpisodeRuntime;
import com.dippie.common.error.InvalidRequestException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

// Danh sách các kệ tâm trạng có sẵn trên giao diện, mỗi mục mang sẵn thể loại, từ khóa không dấu và trần thời lượng riêng.
enum MoodPreset {
    FUNNY("funny", Set.of("comedy"), List.of("vui nhon", "hai huoc", "tro dua", "bat cuoi"), 60),
    FAMILY("family", Set.of("family"), List.of("gia dinh", "cha me", "anh chi em", "yeu thuong"), 60),
    BEDTIME("bedtime", Set.of("family", "slice_of_life"), List.of("nhe nhang", "di ngu", "am ap", "chuc ngu ngon"), 10),
    ADVENTURE("adventure", Set.of("adventure", "fantasy"), List.of("phieu luu", "kham pha", "hanh trinh", "vu tru"), 60),
    FRIENDSHIP("friendship", Set.of("school", "slice_of_life"), List.of("tinh ban", "ban than", "ket ban", "giup do"), 60),
    SHORT("short", Set.of(), List.of(), 10);

    private static final double KEYWORD_HIT_WEIGHT = 2.0;

    private final String id;
    private final Set<String> genres;
    private final List<String> keywords;
    private final long maxDurationMs;

    MoodPreset(String id, Set<String> genres, List<String> keywords, int maxMinutes) {
        this.id = id;
        this.genres = genres;
        this.keywords = keywords;
        this.maxDurationMs = maxMinutes * EpisodeRuntime.MINUTE_MS;
    }

    static MoodPreset require(String id) {
        return Arrays.stream(values()).filter(preset -> preset.id.equals(id)).findFirst()
                .orElseThrow(() -> new InvalidRequestException("Tâm trạng bạn chọn không có trong danh sách."));
    }

    // Kệ bedtime và short chặn tối đa 10 phút; riêng bedtime còn loại luôn các tập có cảnh kịch tính.
    boolean withinRuntime(RecommendationCandidate item) {
        return suitableForMood(item)
                && (maxDurationMs == EpisodeRuntime.HOUR_MS || EpisodeRuntime.atMost(item.durationMs(), maxDurationMs))
                && (this != BEDTIME || !item.features().intense());
    }

    // Mọi kệ tâm trạng đều bỏ tập tổng hợp dài và tập vượt quá 1 tiếng để giữ đúng nhịp xem ngắn gọn.
    static boolean suitableForMood(RecommendationCandidate item) {
        Long duration = item.features().knownDurationMs();
        return (duration == null || duration <= EpisodeRuntime.HOUR_MS) && !item.features().compilation();
    }

    // Kệ short không xếp theo từ khóa nên trả điểm 1 cố định; kệ bedtime bắt buộc phải có calmScore > 0 mới được tính.
    double score(RecommendationCandidate item) {
        if (this == SHORT) {
            return 1;
        }
        int calm = this == BEDTIME ? item.features().calmScore() : 0;
        if (this == BEDTIME && calm == 0) {
            return 0;
        }
        String details = item.features().foldedMoodText();
        long keywordHits = keywords.stream().filter(details::contains).count();
        long genreHits = item.genres().stream().filter(genres::contains).count();
        return calm + keywordHits * KEYWORD_HIT_WEIGHT + genreHits;
    }
}
