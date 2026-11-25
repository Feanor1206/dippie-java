package com.dippie.recommendation;

import java.util.List;
import java.util.Set;

public record RecommendationCandidate(
        String id, String title, String seriesId, String seasonId, Integer episodeNumber, int year,
        double rating, String runtime, String poster, String backdrop, String description,
        List<String> genres, List<String> themes, List<String> keywords, List<String> characters,
        Long startMs, Long endMs, Long durationMs, String seriesTitle
) {
    boolean matchesIds(Set<String> ids) {
        return ids.contains(id) || (seriesId != null && ids.contains(seriesId));
    }
}
