package com.dippie.model.recommendation;

import com.dippie.exception.InvalidRequestException;
import com.dippie.model.catalog.EpisodeRuntime;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

// mấy kệ tâm trạng sẵn trên giao diện, mỗi kệ có thể loại, từ khóa không dấu với trần thời lượng riêng
public enum MoodPreset {
    FUNNY("funny", Set.of("comedy"), List.of("vui nhon", "hai huoc", "tro dua", "bat cuoi"), 60),
    FAMILY("family", Set.of("family"), List.of("gia dinh", "cha me", "anh chi em", "yeu thuong"), 60),
    BEDTIME("bedtime", Set.of("family", "slice_of_life"), List.of("nhe nhang", "di ngu", "am ap", "chuc ngu ngon"), 10),
    ADVENTURE("adventure", Set.of("adventure", "fantasy"), List.of("phieu luu", "kham pha", "hanh trinh", "vu tru"), 60),
    FRIENDSHIP("friendship", Set.of("school", "slice_of_life"), List.of("tinh ban", "ban than", "ket ban", "giup do"), 60),
    SHORT("short", Set.of(), List.of(), 10);


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

    public static MoodPreset require(String id) {
        return Arrays.stream(values()).filter(preset -> preset.id.equals(id)).findFirst()
                .orElseThrow(() -> new InvalidRequestException("Tâm trạng bạn chọn không có trong danh sách."));
    }

    // kệ bedtime với short chặn trần 10 phút, riêng bedtime loại thêm mấy tập kịch tính
    public boolean withinRuntime(RecommendationCandidate item) {
        return suitableForMood(item)
                && (maxDurationMs == EpisodeRuntime.HOUR_MS || EpisodeRuntime.atMost(item.durationMs(), maxDurationMs))
                && (this != BEDTIME || !item.features().intense());
    }

    // kệ tâm trạng nào cũng bỏ tập tổng hợp dài với tập quá 1 tiếng để giữ nhịp xem gọn
    public static boolean suitableForMood(RecommendationCandidate item) {
        Long duration = item.features().knownDurationMs();
        return (duration == null || duration <= EpisodeRuntime.HOUR_MS) && !item.features().compilation();
    }

    // kệ short không chấm từ khóa nên trả điểm 1 cứng, còn bedtime phải có calmScore > 0 mới tính
    public double score(RecommendationCandidate item) {
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
        return calm + keywordHits * 2.0 + genreHits;
    }
}
