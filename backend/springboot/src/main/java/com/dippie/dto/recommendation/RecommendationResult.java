package com.dippie.dto.recommendation;

import java.util.List;

// kết quả API gợi ý gồm list tập phim kèm câu giải thích ngắn hiện lên giao diện
public record RecommendationResult(List<RecommendedItem> recommendedMovies, String reasoning) {

    public RecommendationResult {
        recommendedMovies = recommendedMovies == null ? List.of() : List.copyOf(recommendedMovies);
    }
}
