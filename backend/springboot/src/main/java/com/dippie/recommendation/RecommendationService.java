package com.dippie.recommendation;

import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {

    private final CandidateCatalog catalog;
    private final ViewerSignalsRepository signals;
    private final CurrentViewer currentViewer;
    private final AffinityRecommendationStrategy affinity;
    private final MoodRecommendationStrategy mood;

    RecommendationService(CandidateCatalog catalog, ViewerSignalsRepository signals, CurrentViewer currentViewer,
            AffinityRecommendationStrategy affinity, MoodRecommendationStrategy mood) {
        this.catalog = catalog;
        this.signals = signals;
        this.currentViewer = currentViewer;
        this.affinity = affinity;
        this.mood = mood;
    }

    public RecommendationResult recommend(RecommendationRequest request) {
        Long userId = currentViewer.find().map(Viewer::userId).orElse(null);
        var context = new RecommendationContext(request, catalog.load(), signals.load(userId, request.likedMovieIds()));
        RecommendationStrategy strategy = request.mood() == null || request.mood().isBlank() ? affinity : mood;
        return strategy.recommend(context);
    }
}
