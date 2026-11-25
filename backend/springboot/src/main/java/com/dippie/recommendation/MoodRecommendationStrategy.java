package com.dippie.recommendation;

import com.dippie.assistant.search.CatalogSearch;
import com.dippie.assistant.search.SearchConstraints;
import com.dippie.assistant.search.SearchHit;
import com.dippie.common.error.ServiceUnavailableException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MoodRecommendationStrategy implements RecommendationStrategy {

    private final CatalogSearch search;
    private final AffinityRecommendationStrategy affinity;

    public MoodRecommendationStrategy(CatalogSearch search, AffinityRecommendationStrategy affinity) {
        this.search = search;
        this.affinity = affinity;
    }

    @Override
    public RecommendationResult recommend(RecommendationContext context) {
        var explicit = context.explicitGenres();
        List<SearchHit> hits;
        try {
            if (!search.isReady()) {
                return affinity.recommend(context);
            }
            var constraints = new SearchConstraints(explicit.stream().sorted().toList(), List.of(), null,
                    null, null, null, false);
            hits = search.search(context.request().mood(), 100, constraints);
        } catch (ServiceUnavailableException exception) {
            return affinity.recommend(context);
        }

        Map<String, RecommendationCandidate> candidates = new HashMap<>();
        context.candidates(explicit).forEach(item -> candidates.put(item.id(), item));
        List<RecommendedItem> movies = new ArrayList<>();
        for (SearchHit hit : hits) {
            RecommendationCandidate item = candidates.remove(hit.id());
            if (item != null) {
                Double score = hit.score();
                var evidence = hit.evidence().stream().map(entry -> new RecommendedItem.Evidence(entry.documentId(),
                        entry.text(), entry.startMs(), entry.endMs(), entry.itemType())).toList();
                movies.add(RecommendedItem.from(item, score != null && Double.isFinite(score) ? score : null, evidence));
                if (movies.size() == context.request().limit()) {
                    break;
                }
            }
        }
        return new RecommendationResult(movies, "Dựa trên tâm trạng bạn chọn.");
    }

}
