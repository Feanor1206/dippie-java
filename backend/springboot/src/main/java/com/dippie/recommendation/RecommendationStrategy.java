package com.dippie.recommendation;

// Interface Strategy chung cho các cách gợi ý phim (theo sở thích hoặc theo tâm trạng).
public interface RecommendationStrategy {

    RecommendationResult recommend(RecommendationContext context);
}
