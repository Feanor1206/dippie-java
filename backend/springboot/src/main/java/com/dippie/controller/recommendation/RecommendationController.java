package com.dippie.controller.recommendation;

import com.dippie.dto.recommendation.RecommendationRequest;
import com.dippie.dto.recommendation.RecommendationResult;
import com.dippie.service.recommendation.RecommendationService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// endpoint nhận yêu cầu gợi ý phim từ trang chủ và kệ chọn tâm trạng
@RestController
public class RecommendationController {

    private final RecommendationService recommendations;

    public RecommendationController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @PostMapping("/api/recommend")
    public RecommendationResult recommend(@Valid @RequestBody RecommendationRequest request) {
        return recommendations.recommend(request);
    }
}
