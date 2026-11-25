package com.dippie.recommendation;

import com.dippie.ai.AiServiceClient;
import com.dippie.ai.AiUnavailableException;
import com.dippie.ai.AiUpstreamException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MoodRecommendationStrategy implements RecommendationStrategy {

    private final AiServiceClient ai;
    private final ObjectMapper mapper;
    private final AffinityRecommendationStrategy affinity;

    public MoodRecommendationStrategy(AiServiceClient ai, ObjectMapper mapper, AffinityRecommendationStrategy affinity) {
        this.ai = ai;
        this.mapper = mapper;
        this.affinity = affinity;
    }

    @Override
    public RecommendationResult recommend(RecommendationContext context) {
        var explicit = context.explicitGenres();
        var request = mapper.createObjectNode().put("query", context.request().mood()).put("limit", 100);
        var genres = request.putObject("constraints").putArray("include_genres");
        explicit.stream().sorted().forEach(genres::add);

        JsonNode response;
        try {
            response = ai.search(request);
        } catch (AiUnavailableException | AiUpstreamException exception) {
            return affinity.recommend(context);
        }
        if (response == null || !response.path("results").isArray()) {
            return affinity.recommend(context);
        }

        Map<String, RecommendationCandidate> candidates = new HashMap<>();
        context.candidates(explicit).forEach(item -> candidates.put(item.id(), item));
        List<RecommendedItem> movies = new ArrayList<>();
        for (JsonNode hit : response.path("results")) {
            RecommendationCandidate item = candidates.remove(hit.path("id").textValue());
            if (item != null) {
                movies.add(RecommendedItem.from(item, score(hit.path("score")), evidence(hit.path("evidence"))));
                if (movies.size() == context.request().limit()) {
                    break;
                }
            }
        }
        return new RecommendationResult(movies, "Dựa trên tâm trạng bạn chọn.");
    }

    private static Double score(JsonNode score) {
        return score.isNumber() && Double.isFinite(score.doubleValue()) ? score.doubleValue() : null;
    }

    private static List<RecommendedItem.Evidence> evidence(JsonNode entries) {
        if (!entries.isArray()) {
            return List.of();
        }
        List<RecommendedItem.Evidence> evidence = new ArrayList<>();
        for (JsonNode entry : entries) {
            if (entry.isObject()) {
                evidence.add(new RecommendedItem.Evidence(entry.path("documentId").textValue(),
                        entry.path("text").textValue(), timestamp(entry.path("startMs")), timestamp(entry.path("endMs")),
                        entry.path("itemType").textValue()));
            }
        }
        return evidence;
    }

    private static Long timestamp(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToLong() ? value.longValue() : null;
    }
}
