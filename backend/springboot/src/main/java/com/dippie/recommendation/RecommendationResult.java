package com.dippie.recommendation;

import java.util.List;

record RecommendationResult(List<RecommendedItem> recommendedMovies, String reasoning) {

    RecommendationResult {
        recommendedMovies = recommendedMovies == null ? List.of() : List.copyOf(recommendedMovies);
    }
}
