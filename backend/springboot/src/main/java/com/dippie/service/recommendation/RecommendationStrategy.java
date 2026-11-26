package com.dippie.service.recommendation;

import com.dippie.dto.recommendation.RecommendationResult;

// kiểu Strategy cho các cách gợi ý phim, thêm kiểu gợi ý mới thì viết thêm 1 lớp là xong
public interface RecommendationStrategy {

    RecommendationResult recommend(RecommendationContext context);
}
