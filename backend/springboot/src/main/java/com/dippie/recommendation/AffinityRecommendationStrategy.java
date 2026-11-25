package com.dippie.recommendation;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import static com.dippie.recommendation.RecommendationContext.tags;

@Component
public class AffinityRecommendationStrategy implements RecommendationStrategy {

    @Override
    public RecommendationResult recommend(RecommendationContext context) {
        Set<String> explicit = context.explicitGenres();
        Preferences preferences = new Preferences(new HashSet<>(explicit), new HashSet<>(), new HashSet<>(), new HashSet<>());
        var liked = context.catalog().stream().filter(item -> item.matchesIds(context.signals().liked())).toList();
        liked.forEach(preferences::add);

        var movies = context.candidates(explicit).stream()
                .map(item -> new Scored(item, preferences.score(item)))
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparing(Comparator.comparingDouble(Scored::rating).reversed())
                        .thenComparing(scored -> scored.item().id()))
                .limit(context.request().limit())
                .map(scored -> RecommendedItem.affinity(scored.item(), scored.score()))
                .toList();

        if (!liked.isEmpty()) {
            return new RecommendationResult(movies, "Phim tương đồng với nội dung bạn đã thích hoặc đã xem.");
        }
        String reasoning = explicit.isEmpty() ? "Gợi ý từ kho phim." : "Dựa trên thể loại bạn chọn.";
        return new RecommendationResult(movies, reasoning);
    }

    private record Preferences(Set<String> genres, Set<String> themes, Set<String> characters, Set<String> series) {
        void add(RecommendationCandidate item) {
            genres.addAll(tags(item.genres()));
            themes.addAll(themeTags(item));
            characters.addAll(tags(item.characters()));
            if (item.seriesId() != null && !item.seriesId().isEmpty()) {
                series.add(item.seriesId());
            }
        }

        double score(RecommendationCandidate item) {
            double score = item.rating() * 0.1;
            score += overlap(2.0, tags(item.genres()), genres);
            score += overlap(1.5, themeTags(item), themes);
            score += overlap(1.2, tags(item.characters()), characters);
            score += series.contains(item.seriesId()) ? 0.8 : 0;
            return new BigDecimal(score).setScale(4, RoundingMode.HALF_EVEN).doubleValue();
        }

        private static Set<String> themeTags(RecommendationCandidate item) {
            Set<String> tags = tags(item.themes());
            tags.addAll(tags(item.keywords()));
            return tags;
        }

        private static double overlap(double weight, Set<String> item, Set<String> preferred) {
            long matched = item.stream().filter(preferred::contains).count();
            return weight * matched / Math.max(1, preferred.size());
        }
    }

    private record Scored(RecommendationCandidate item, double score) {
        double rating() {
            return item.rating();
        }
    }
}
