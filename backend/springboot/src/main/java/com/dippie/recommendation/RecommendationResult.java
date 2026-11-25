package com.dippie.recommendation;

import java.util.List;

public record RecommendationResult(List<RecommendedItem> recommendedMovies, String reasoning) {
}
