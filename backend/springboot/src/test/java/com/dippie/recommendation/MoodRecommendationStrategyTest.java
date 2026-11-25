package com.dippie.recommendation;

import com.dippie.ai.AiServiceClient;
import com.dippie.ai.AiUnavailableException;
import com.dippie.ai.AiUpstreamException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static com.dippie.recommendation.AffinityRecommendationStrategyTest.candidate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MoodRecommendationStrategyTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AiServiceClient client = mock(AiServiceClient.class);
    private final AffinityRecommendationStrategy affinity = new AffinityRecommendationStrategy();
    private final MoodRecommendationStrategy mood = new MoodRecommendationStrategy(client, mapper, affinity);

    @Test
    void preservesHitOrderAndFiltersUnknownSeenAndDuplicateIds() throws Exception {
        when(client.search(any())).thenReturn(mapper.readTree("""
                {"results":[{"id":"unknown"},{"id":"seen"},{"id":"wrong"},{"id":"second","score":0.9,
                 "primary_media_asset_id":"private-asset","evidence":[{"documentId":"doc","text":"Evidence",
                 "startMs":10,"endMs":20,"itemType":"segment"}]},
                 {"id":"second"},{"id":"first","score":0.8},{"id":"extra"}]}
                """));

        var result = mood.recommend(context());

        var request = ArgumentCaptor.forClass(JsonNode.class);
        verify(client).search(request.capture());
        assertThat(request.getValue()).isEqualTo(mapper.readTree("""
                {"query":"funny","limit":100,"constraints":{"include_genres":["drama"]}}
                """));
        assertThat(result.reasoning()).isEqualTo("Dựa trên tâm trạng bạn chọn.");
        assertThat(result.recommendedMovies()).extracting(RecommendedItem::id).containsExactly("second", "first");
        var item = result.recommendedMovies().getFirst();
        assertThat(item.score()).isEqualTo(0.9);
        assertThat(item.primaryMediaAssetId()).isNull();
        assertThat(item.evidence()).containsExactly(new RecommendedItem.Evidence("doc", "Evidence", 10L, 20L, "segment"));
    }

    @Test
    void fallsBackToAffinityForConnectionFailuresAndUnavailableSearch() {
        when(client.search(any())).thenThrow(new AiUnavailableException(new IOException()))
                .thenThrow(new AiUpstreamException(503, "application/json", null));

        var expected = affinity.recommend(context());
        assertThat(mood.recommend(context())).isEqualTo(expected);
        assertThat(mood.recommend(context())).isEqualTo(expected);
    }

    private static RecommendationContext context() {
        return new RecommendationContext(new RecommendationRequest(List.of("Drama"), null, "funny", 2),
                List.of(candidate("first", 8, "Drama"), candidate("second", 7, "Drama"),
                        candidate("seen", 9, "Drama"), candidate("wrong", 10, "Other"), candidate("extra", 6, "Drama")),
                new ViewerSignals(Set.of(), Set.of("seen")));
    }
}
