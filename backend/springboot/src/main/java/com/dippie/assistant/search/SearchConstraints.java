package com.dippie.assistant.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record SearchConstraints(
        @JsonProperty("include_genres") List<String> includeGenres,
        @JsonProperty("exclude_genres") List<String> excludeGenres,
        @JsonProperty("year_min") Integer yearMin,
        @JsonProperty("year_max") Integer yearMax,
        @JsonProperty("season_number") Integer seasonNumber,
        @JsonProperty("episode_number") Integer episodeNumber,
        @JsonProperty("series_only") boolean seriesOnly
) {

    public static final SearchConstraints NONE = new SearchConstraints(List.of(), List.of(), null, null, null, null, false);

    public SearchConstraints {
        includeGenres = includeGenres == null ? List.of() : List.copyOf(includeGenres);
        excludeGenres = excludeGenres == null ? List.of() : List.copyOf(excludeGenres);
    }
}
