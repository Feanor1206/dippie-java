package com.dippie.recommendation;

import com.dippie.catalog.domain.EpisodeMood;
import com.dippie.catalog.domain.EpisodeRuntime;
import com.dippie.common.text.TextFolding;
import com.dippie.common.text.UnicodeText;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Một tập phim ứng viên dùng chung giữa các Strategy gợi ý, kèm sẵn bộ đặc trưng đã tính trước để khỏi xử lý lại mỗi request.
public record RecommendationCandidate(
        String id, String title, String seriesId, String seasonId, Integer episodeNumber, Integer year,
        double rating, String runtime, String poster, String backdrop, String description,
        List<String> genres, List<String> genreTerms, List<String> themes, List<String> keywords, List<String> characters,
        Long startMs, Long endMs, Long durationMs, String seriesTitle, Integer seasonNumber, int summaryQuality,
        Features features
) {
    public RecommendationCandidate(
            String id, String title, String seriesId, String seasonId, Integer episodeNumber, Integer year,
            double rating, String runtime, String poster, String backdrop, String description,
            List<String> genres, List<String> genreTerms, List<String> themes, List<String> keywords, List<String> characters,
            Long startMs, Long endMs, Long durationMs, String seriesTitle, Integer seasonNumber, int summaryQuality
    ) {
        this(id, title, seriesId, seasonId, episodeNumber, year, rating, runtime, poster, backdrop, description,
                genres, genreTerms, themes, keywords, characters, startMs, endMs, durationMs, seriesTitle,
                seasonNumber, summaryQuality,
                Features.from(seriesId, title, description, genreTerms, themes, keywords, characters, durationMs));
    }

    // Tín hiệu thích hay không thích có thể gắn theo mã tập hoặc mã cả bộ phim nên phải kiểm cả hai.
    boolean matchesPreference(Set<String> ids) {
        return ids.contains(id) || (seriesId != null && ids.contains(seriesId));
    }

    // Đặc trưng đã hạ chữ thường và bỏ dấu từ trước để các vòng chấm điểm sở thích, tâm trạng chạy nhanh.
    public record Features(
            Set<String> genreTags,
            Set<String> themeTags,
            Set<String> characterTags,
            String foldedMoodText,
            boolean compilation,
            boolean intense,
            int calmScore,
            Long knownDurationMs
    ) {
        // Gom themes với keywords vào chung themeTags, đồng thời tính trước cờ tập tổng hợp, cờ kịch tính và điểm êm dịu.
        static Features from(String seriesId, String title, String description, List<String> genreTerms,
                List<String> themes, List<String> keywords, List<String> characters, Long durationMs) {
            Set<String> foldedThemes = foldSet(themes);
            Set<String> combinedThemes = new HashSet<>(foldedThemes);
            combinedThemes.addAll(foldSet(keywords));
            String moodText = TextFolding.fold(String.join(" ",
                    title == null ? "" : title,
                    description == null ? "" : description,
                    keywords == null ? "" : String.join(" ", keywords),
                    themes == null ? "" : String.join(" ", themes)));
            String normalizedDetails = TextFolding.collapseWhitespace(moodText.replace('_', ' ').replace('-', ' '));
            return new Features(
                    foldSet(genreTerms),
                    Set.copyOf(combinedThemes),
                    foldSet(characters),
                    moodText,
                    EpisodeMood.compilation(title, keywords, themes),
                    EpisodeMood.intenseFolded(normalizedDetails),
                    EpisodeMood.calmScoreFolded(seriesId, normalizedDetails),
                    EpisodeRuntime.known(durationMs));
        }

        private static Set<String> foldSet(List<String> tags) {
            if (tags == null || tags.isEmpty()) {
                return Set.of();
            }
            return tags.stream().map(UnicodeText::caseFold).collect(Collectors.toUnmodifiableSet());
        }
    }
}
