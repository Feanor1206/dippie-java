package com.dippie.recommendation;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
