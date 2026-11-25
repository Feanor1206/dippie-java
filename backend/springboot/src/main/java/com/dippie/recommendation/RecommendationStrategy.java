package com.dippie.recommendation;

public interface RecommendationStrategy {

    RecommendationResult recommend(RecommendationContext context);
}
