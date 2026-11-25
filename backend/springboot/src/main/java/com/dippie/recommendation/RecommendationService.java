package com.dippie.recommendation;

import com.dippie.common.rating.RatingLookup;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import org.springframework.stereotype.Service;

// Facade của phần gợi ý phim: nạp ngữ cảnh người xem, chọn Strategy phù hợp rồi ghép thêm điểm đánh giá vào kết quả.
@Service
public class RecommendationService {

    private final CandidateCatalog catalog;
    private final ViewerSignalsRepository signals;
    private final CurrentViewer currentViewer;
    private final AffinityRecommendationStrategy affinity;
    private final MoodRecommendationStrategy mood;
    private final RatingLookup ratings;

    RecommendationService(CandidateCatalog catalog, ViewerSignalsRepository signals, CurrentViewer currentViewer,
            AffinityRecommendationStrategy affinity, MoodRecommendationStrategy mood, RatingLookup ratings) {
        this.catalog = catalog;
        this.signals = signals;
        this.currentViewer = currentViewer;
        this.affinity = affinity;
        this.mood = mood;
        this.ratings = ratings;
    }

    // Request có mood hoặc moodPreset thì chạy MoodRecommendationStrategy, còn lại chạy AffinityRecommendationStrategy.
    public RecommendationResult recommend(RecommendationRequest request) {
        Long userId = currentViewer.find().map(Viewer::userId).orElse(null);
        var context = new RecommendationContext(request, catalog.load(), signals.load(userId, request.likedMovieIds()));
        boolean wantsMood = request.moodPreset() != null || (request.mood() != null && !request.mood().isBlank());
        RecommendationStrategy strategy = wantsMood ? mood : affinity;
        var result = strategy.recommend(context);
        var items = ratings.withSummaries(result.recommendedMovies(), RecommendedItem::id, RecommendedItem::withRatings);
        return new RecommendationResult(items, result.reasoning());
    }
}
