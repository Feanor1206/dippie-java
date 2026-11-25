package com.dippie.recommendation;

import java.util.List;

// Kết quả trả về của API gợi ý, gồm danh sách tập phim và câu lý do ngắn hiển thị trên giao diện.
record RecommendationResult(List<RecommendedItem> recommendedMovies, String reasoning) {

    RecommendationResult {
        recommendedMovies = recommendedMovies == null ? List.of() : List.copyOf(recommendedMovies);
    }
}
