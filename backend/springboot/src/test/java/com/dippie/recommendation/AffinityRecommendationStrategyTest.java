package com.dippie.recommendation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AffinityRecommendationStrategyTest {

    private final AffinityRecommendationStrategy strategy = new AffinityRecommendationStrategy();

    @Test
    void scoresFromLikedEpisodesAndFiltersSeenEpisodesAndExplicitGenres() {
        var liked = candidate("liked", "series", 8, List.of("Action", "Drama"),
                List.of("family", "coming-of-age"), List.of("friends"), List.of("Jadoo", "Mimi"));
        var match = candidate("match", "series", 8.75, List.of("DRAMA"),
                List.of("FRIENDS"), List.of("Family"), List.of("JADOO"));
        var wrongGenre = candidate("wrong-genre", "series", 10, List.of("Action"), List.of(), List.of(), List.of());
        var seen = candidate("seen", "series", 10, List.of("Drama"), List.of(), List.of(), List.of());
        var seenSeries = candidate("other-episode", "seen-series", 10, List.of("Drama"),
                List.of(), List.of(), List.of());

        var result = strategy.recommend(context(List.of(liked, match, wrongGenre, seen, seenSeries), List.of("drama"),
                Set.of("liked"), Set.of("liked", "seen", "seen-series")));

        assertThat(result.reasoning()).isEqualTo("Phim tương đồng với nội dung bạn đã thích hoặc đã xem.");
        assertThat(result.recommendedMovies()).extracting(RecommendedItem::id).containsExactly("match");
        assertThat(result.recommendedMovies().getFirst().score()).isEqualTo(4.275);
    }

    @Test
    void roundsHalfEvenAndSortsByRoundedScoreThenRatingThenId() {
        var result = strategy.recommend(context(List.of(
                candidate("b", 8.0002, "Drama"), candidate("a", 8.0002, "Drama"), candidate("c", 8.0001, "Drama"),
                candidate("top", 8.125, "Drama"), candidate("bottom", 6.25, "Drama")), List.of(), Set.of(), Set.of()));

        assertThat(result.reasoning()).isEqualTo("Gợi ý từ kho phim.");
        assertThat(result.recommendedMovies()).extracting(RecommendedItem::id).containsExactly("top", "a", "b", "c", "bottom");
        assertThat(result.recommendedMovies()).extracting(RecommendedItem::score)
                .containsExactly(0.8125, 0.8, 0.8, 0.8, 0.625);
    }

    @Test
    void roundsBinaryFloatingPointLikePythonAndFoldsGenreNames() {
        var result = strategy.recommend(context(List.of(candidate("match", 8.0625, "Straße"),
                candidate("miss", 10, "Other")), List.of("STRASSE"), Set.of(), Set.of()));

        assertThat(result.reasoning()).isEqualTo("Dựa trên thể loại bạn chọn.");
        assertThat(result.recommendedMovies()).extracting(RecommendedItem::id).containsExactly("match");
        assertThat(result.recommendedMovies().getFirst().score()).isEqualTo(2.8062);
    }

    private static RecommendationContext context(List<RecommendationCandidate> catalog, List<String> genres,
            Set<String> liked, Set<String> seen) {
        return new RecommendationContext(new RecommendationRequest(genres, null, null, 10), catalog,
                new ViewerSignals(liked, seen));
    }

    static RecommendationCandidate candidate(String id, double rating, String genre) {
        return candidate(id, "series", rating, List.of(genre), List.of(), List.of(), List.of());
    }

    private static RecommendationCandidate candidate(String id, String series, double rating, List<String> genres,
            List<String> themes, List<String> keywords, List<String> characters) {
        return new RecommendationCandidate(id, id, series, "season", 1, 2026, rating, "20m", null, null,
                "Synopsis", genres, themes, keywords, characters, null, null, 1_200_000L, "Series");
    }
}
