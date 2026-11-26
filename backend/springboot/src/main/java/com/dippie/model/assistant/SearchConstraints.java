package com.dippie.model.assistant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Set;

// bộ lọc bất biến từ API khám phá hay CineBot đưa vào LexicalIndex
// excludedItems với allowExternalLookup gắn @JsonIgnore vì chỉ dùng nội bộ giữa phiên chat và index
public record SearchConstraints(
        @JsonProperty("include_genres") List<String> includeGenres,
        @JsonProperty("exclude_genres") List<String> excludeGenres,
        @JsonProperty("year_min") Integer yearMin,
        @JsonProperty("year_max") Integer yearMax,
        @JsonProperty("season_number") Integer seasonNumber,
        @JsonProperty("episode_number") Integer episodeNumber,
        @JsonProperty("series_only") boolean seriesOnly,
        @JsonProperty("max_duration_ms") Long maxDurationMs,
        @JsonProperty("prefer_short") boolean preferShort,
        @JsonProperty("episodes_only") boolean episodesOnly,
        @JsonProperty("discovery") boolean discovery,
        @JsonProperty("prefer_evidence") boolean preferEvidence,
        @JsonIgnore Set<String> excludedItems,
        @JsonIgnore boolean allowExternalLookup
) {

    public static final SearchConstraints NONE = new SearchConstraints(List.of(), List.of(), null, null, null, null, false);

    public SearchConstraints(List<String> includeGenres, List<String> excludeGenres, Integer yearMin, Integer yearMax,
            Integer seasonNumber, Integer episodeNumber, boolean seriesOnly) {
        this(includeGenres, excludeGenres, yearMin, yearMax, seasonNumber, episodeNumber, seriesOnly,
                null, false, false, false, false, Set.of(), false);
    }

    public SearchConstraints {
        includeGenres = includeGenres == null ? List.of() : List.copyOf(includeGenres);
        excludeGenres = excludeGenres == null ? List.of() : List.copyOf(excludeGenres);
        excludedItems = excludedItems == null ? Set.of() : Set.copyOf(excludedItems);
    }
}
