package com.dippie.assistant.chat;

import com.dippie.assistant.search.SearchConstraints;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ChatIntent(
        @JsonProperty("intent_type") String intentType,
        @JsonProperty("mode") String mode,
        @JsonProperty("scope") String scope,
        @JsonProperty("semantic_query") String semanticQuery,
        @JsonProperty("constraints") Map<String, Object> constraints,
        @JsonProperty("requested_k") int requestedK,
        @JsonProperty("series_info") String seriesInfo,
        @JsonProperty("include_genres") List<String> includeGenres,
        @JsonProperty("exclude_genres") List<String> excludeGenres,
        @JsonProperty("mood_recommendation") boolean moodRecommendation,
        @JsonProperty("mood_target") String moodTarget
) {

    public ChatIntent {
        constraints = constraints == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(constraints));
        includeGenres = includeGenres == null ? List.of() : List.copyOf(includeGenres);
        excludeGenres = excludeGenres == null ? List.of() : List.copyOf(excludeGenres);
    }

    public SearchConstraints toSearchConstraints() {
        Integer yearMin = constraints.get("year_min") instanceof Number n ? n.intValue() : null;
        Integer yearMax = constraints.get("year_max") instanceof Number n ? n.intValue() : null;
        boolean seriesOnly = Boolean.TRUE.equals(constraints.get("series_only"));
        return new SearchConstraints(includeGenres, excludeGenres, yearMin, yearMax, null, null, seriesOnly);
    }
}
