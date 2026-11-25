package com.dippie.recommendation;

import com.dippie.assistant.search.CatalogSearch;
import com.dippie.assistant.search.SearchConstraints;
import com.dippie.assistant.search.SearchEvidence;
import com.dippie.assistant.search.SearchHit;
import com.dippie.common.error.ServiceUnavailableException;
import com.dippie.common.rating.RatingSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.dippie.recommendation.AffinityRecommendationStrategyTest.candidate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Kiểm tra gợi ý theo tâm trạng: thứ tự kết quả tìm kiếm, biên thời lượng của preset và nhánh lùi về gợi ý theo sở thích.
class MoodRecommendationStrategyTest {

    private final CatalogSearch search = mock(CatalogSearch.class);
    private final AffinityRecommendationStrategy affinity = new AffinityRecommendationStrategy();
    private final MoodRecommendationStrategy mood = new MoodRecommendationStrategy(search, affinity);

    @BeforeEach
    void ready() {
        when(search.isReady()).thenReturn(true);
    }

    @Test
    void preservesHitOrderAndFiltersUnknownSeenAndDuplicateIds() {
        when(search.search(anyString(), anyInt(), any())).thenReturn(List.of(
                hit("unknown", null, List.of()),
                hit("seen", null, List.of()),
                hit("wrong", null, List.of()),
                hit("second", 0.9, List.of(new SearchEvidence("doc", "Evidence", 10L, 20L, "segment"))),
                hit("second", null, List.of()),
                hit("first", 0.8, List.of()),
                hit("extra", null, List.of())
        ));

        var result = mood.recommend(context());

        verify(search).search("funny", 100,
                new SearchConstraints(List.of("drama"), List.of(), null, null, null, null, false));
        assertThat(result.reasoning()).isEqualTo("Phim hợp với tâm trạng bạn chọn.");
        assertThat(result.recommendedMovies()).extracting(RecommendedItem::id).containsExactly("second", "first");
        var item = result.recommendedMovies().getFirst();
        assertThat(item.score()).isEqualTo(0.9);
        assertThat(item.primaryMediaAssetId()).isNull();
        assertThat(item.evidence()).containsExactly(new RecommendedItem.Evidence("doc", "Evidence", 10L, 20L, "segment"));
    }

    @Test
    void fallsBackToAffinityForUnavailableSearch() {
        when(search.search(anyString(), anyInt(), any()))
                .thenThrow(new ServiceUnavailableException("unavailable"));

        var expected = affinity.recommend(context());
        assertThat(mood.recommend(context())).isEqualTo(expected);
        when(search.isReady()).thenReturn(false);
        assertThat(mood.recommend(context())).isEqualTo(expected);
    }

    @Test
    void fallsBackToAffinityWhenAPresetMatchesNoCatalogThemes() {
        var original = context();
        var request = new RecommendationRequest(List.of("Drama"), null, null, "adventure", null, 2);
        var presetContext = new RecommendationContext(request, original.catalog(), original.signals());
        assertThat(mood.recommend(presetContext)).isEqualTo(affinity.recommend(presetContext));
    }

    @Test
    void shortShelfIncludesBothRuntimeBoundariesAndRejectsCompilationTags() {
        assertThat(MoodPreset.SHORT.withinRuntime(episode("Tập ngắn", 59_999, List.of()))).isFalse();
        assertThat(MoodPreset.SHORT.withinRuntime(episode("Tập ngắn", 60_000, List.of()))).isTrue();
        assertThat(MoodPreset.SHORT.withinRuntime(episode("Tập ngắn", 600_000, List.of()))).isTrue();
        assertThat(MoodPreset.SHORT.withinRuntime(episode("Tập dài", 600_001, List.of()))).isFalse();
        assertThat(MoodPreset.SHORT.withinRuntime(episode("Tập ngắn", 60_000, List.of("bộ_sưu_tập")))).isFalse();
        assertThat(MoodPreset.BEDTIME.withinRuntime(episode("Tập ngắn", 60_000, List.of("tổng hợp")))).isFalse();
        assertThat(MoodPreset.FAMILY.withinRuntime(episode("[2-hour] Tình bạn", 300_000, List.of()))).isFalse();
        assertThat(MoodPreset.FAMILY.withinRuntime(episode("Tập dài", 3_600_001, List.of()))).isFalse();
    }

    private static RecommendationCandidate episode(String title, long duration, List<String> keywords) {
        return new RecommendationCandidate("episode", title, "series", null, null, null, 0, "Tập phim", null,
                null, "", List.of(), List.of(), List.of(), keywords, List.of(), null, null, duration, "Series", null, 0);
    }

    private static SearchHit hit(String id, Double score, List<SearchEvidence> evidence) {
        return new SearchHit(id, id, 0, 0.0, null, List.of(), null, null, null, null, null, "episode", null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, score, null, evidence, null, null, false, RatingSummary.EMPTY);
    }

    private static RecommendationContext context() {
        return new RecommendationContext(new RecommendationRequest(List.of("Drama"), null, "funny", null, null, 2),
                List.of(candidate("first", 8, "Drama"), candidate("second", 7, "Drama"),
                        candidate("seen", 9, "Drama"), candidate("wrong", 10, "Other"), candidate("extra", 6, "Drama")),
                new ViewerSignals(Set.of(), Set.of("seen"), Set.of()));
    }
}
