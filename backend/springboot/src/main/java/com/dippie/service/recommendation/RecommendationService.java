package com.dippie.service.recommendation;

import com.dippie.common.rating.RatingLookup;
import com.dippie.dto.recommendation.RecommendationRequest;
import com.dippie.dto.recommendation.RecommendationResult;
import com.dippie.dto.recommendation.RecommendedItem;
import com.dippie.repository.recommendation.ViewerSignalsRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;

import org.springframework.stereotype.Service;

// kiểu Facade cho phần gợi ý phim, gom ngữ cảnh người xem, chọn Strategy hợp rồi gắn thêm điểm đánh giá
@Service
public class RecommendationService {

    private final CandidateCatalog catalog;
    private final ViewerSignalsRepository signals;
    private final CurrentViewer currentViewer;
    private final AffinityRecommendationStrategy affinity;
    private final MoodRecommendationStrategy mood;
    private final RatingLookup ratings;

    public RecommendationService(CandidateCatalog catalog, ViewerSignalsRepository signals, CurrentViewer currentViewer,
            AffinityRecommendationStrategy affinity, MoodRecommendationStrategy mood, RatingLookup ratings) {
        this.catalog = catalog;
        this.signals = signals;
        this.currentViewer = currentViewer;
        this.affinity = affinity;
        this.mood = mood;
        this.ratings = ratings;
    }

    // có mood hay moodPreset thì chạy MoodRecommendationStrategy, không thì chạy AffinityRecommendationStrategy
    public RecommendationResult recommend(RecommendationRequest request) {
        Viewer viewer = currentViewer.find().orElse(null);
        Long userId = viewer == null ? null : viewer.userId();
        long profileScopeId = viewer == null ? 0L : viewer.profileScopeId();
        var context = new RecommendationContext(
                request,
                catalog.load(),
                signals.load(userId, profileScopeId, request.likedMovieIds())
        );
        boolean wantsMood = request.moodPreset() != null || (request.mood() != null && !request.mood().isBlank());
        RecommendationStrategy strategy = wantsMood ? mood : affinity;
        var result = strategy.recommend(context);
        var items = ratings.withSummaries(result.recommendedMovies(), RecommendedItem::id, RecommendedItem::withRatings);
        return new RecommendationResult(items, result.reasoning());
    }
}
