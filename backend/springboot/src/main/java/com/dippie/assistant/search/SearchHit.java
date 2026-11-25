package com.dippie.assistant.search;

import com.dippie.common.rating.RatingSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Kết quả tìm kiếm trả chung cho CineBot và API khám phá.
// Phải giữ song song cặp trường camelCase (episodeId, startMs) và snake_case (episode_id, start_ms) để tương thích payload frontend cũ.
public record SearchHit(
        String id,
        String title,
        Integer year,
        double rating,
        String runtime,
        List<String> genres,
        String description,
        String poster,
        String backdrop,
        String videoUrl,
        Integer matchPercentage,
        @JsonProperty("item_type") String itemType,
        @JsonProperty("series_id") String seriesId,
        @JsonProperty("season_id") String seasonId,
        @JsonProperty("episode_id") String episodeIdSnake,
        @JsonProperty("segment_id") String segmentId,
        @JsonProperty("episode_number") Integer episodeNumber,
        @JsonProperty("air_date") String airDate,
        @JsonProperty("duration_ms") Long durationMs,
        @JsonProperty("start_ms") Long startMsSnake,
        @JsonProperty("end_ms") Long endMs,
        @JsonProperty("primary_media_asset_id") String primaryMediaAssetId,
        @JsonProperty("media_asset_id") String mediaAssetId,
        List<String> aliases,
        List<String> characters,
        @JsonProperty("plot_facts") List<String> plotFacts,
        List<String> keywords,
        List<String> themes,
        @JsonProperty("visual_summary") String visualSummary,
        @JsonProperty("dialogue_summary") String dialogueSummary,
        @JsonProperty("sound_events") List<String> soundEvents,
        @JsonProperty("series_title") String seriesTitle,
        @JsonProperty("episode_title") String episodeTitle,
        @JsonProperty("comparative_attributes") Map<String, Object> comparativeAttributes,
        Map<String, Object> diagnostics,
        String episodeId,
        Long startMs,
        Double score,
        Double calibratedScore,
        List<SearchEvidence> evidence,
        @JsonProperty("episode_count") @JsonInclude(JsonInclude.Include.NON_NULL) Integer episodeCount,
        @JsonProperty("season_count") @JsonInclude(JsonInclude.Include.NON_NULL) Integer seasonCount,
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean externalOnly,
        @JsonUnwrapped RatingSummary ratings
) {

    public SearchHit {
        genres = genres == null ? List.of() : List.copyOf(genres);
        aliases = copyList(aliases);
        characters = copyList(characters);
        plotFacts = copyList(plotFacts);
        keywords = copyList(keywords);
        themes = copyList(themes);
        soundEvents = copyList(soundEvents);
        comparativeAttributes = copyMap(comparativeAttributes);
        diagnostics = copyMap(diagnostics);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    // Ưu tiên mốc trên dòng thời gian tập phim, chỉ dùng mốc tệp nguồn khi tập chưa có offset riêng.
    public Long effectiveStartMs() {
        return startMs != null ? startMs : startMsSnake;
    }

    public SearchHit withRatings(RatingSummary summary) {
        return new SearchHit(id, title, year, rating, runtime, genres, description, poster, backdrop, videoUrl,
                matchPercentage, itemType, seriesId, seasonId, episodeIdSnake, segmentId, episodeNumber, airDate,
                durationMs, startMsSnake, endMs, primaryMediaAssetId, mediaAssetId, aliases, characters, plotFacts,
                keywords, themes, visualSummary, dialogueSummary, soundEvents, seriesTitle, episodeTitle,
                comparativeAttributes, diagnostics, episodeId, startMs, score, calibratedScore, evidence,
                episodeCount, seasonCount, externalOnly, summary);
    }

    private static List<String> copyList(List<String> values) {
        return values == null ? null : List.copyOf(values);
    }

    private static Map<String, Object> copyMap(Map<String, Object> values) {
        return values == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
