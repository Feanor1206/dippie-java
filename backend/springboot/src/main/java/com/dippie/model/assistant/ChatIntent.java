package com.dippie.model.assistant;

import com.dippie.common.text.TextFolding;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// ý định câu hỏi IntentParser tách ra (mode, phạm vi, thể loại, tâm trạng, bộ lọc), JSON xuất key snake_case
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

    private static final Pattern EPISODE_REQUEST = Pattern.compile("\\b(?:tap|episode)\\b");
    private static final Pattern SUMMARY_REQUEST = Pattern.compile("\\b(?:tom tat|ke lai)\\b");
    private static final Pattern EPISODE_MOMENT = Pattern.compile("\\btap\\b.+\\bnao\\b");

    public ChatIntent {
        constraints = constraints == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(constraints));
        includeGenres = includeGenres == null ? List.of() : List.copyOf(includeGenres);
        excludeGenres = excludeGenres == null ? List.of() : List.copyOf(excludeGenres);
    }

    public SearchConstraints toSearchConstraints() {
        return toSearchConstraints(Set.of());
    }

    // gom hết bộ lọc với mấy tập cần bỏ qua sang SearchConstraints rồi đẩy xuống CatalogSearch
    public SearchConstraints toSearchConstraints(Set<String> excludedItems) {
        Integer yearMin = constraints.get("year_min") instanceof Number number ? number.intValue() : null;
        Integer yearMax = constraints.get("year_max") instanceof Number number ? number.intValue() : null;
        boolean seriesOnly = Boolean.TRUE.equals(constraints.get("series_only"));
        Long maxDuration = constraints.get("max_duration_ms") instanceof Number number ? number.longValue() : null;
        boolean shortEpisodes = Boolean.TRUE.equals(constraints.get("prefer_short"));
        boolean discovery = "recommend".equals(mode) || (requestedK > 1 && "episode".equals(scope));
        boolean preferEvidence = !discovery && ("scene".equals(scope)
                || EPISODE_MOMENT.matcher(TextFolding.fold(semanticQuery)).find());
        return new SearchConstraints(includeGenres, excludeGenres, yearMin, yearMax, null, null, seriesOnly,
                maxDuration, shortEpisodes, discovery && (shortEpisodes || asksForEpisodes()), discovery, preferEvidence,
                excludedItems, "find_moment".equals(mode));
    }

    public boolean asksForEpisodes() {
        return EPISODE_REQUEST.matcher(TextFolding.fold(semanticQuery)).find();
    }

    public boolean summaryRequested() {
        return SUMMARY_REQUEST.matcher(TextFolding.fold(semanticQuery)).find();
    }

    public String similarTo() {
        return constraints.get("similar_to") instanceof String title ? title : null;
    }
}
