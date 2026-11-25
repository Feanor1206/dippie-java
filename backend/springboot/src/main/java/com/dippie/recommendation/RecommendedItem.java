package com.dippie.recommendation;

import com.dippie.common.rating.RatingSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.List;
import java.util.Map;

// DTO từng tập phim gợi ý trả về JSON; giữ đủ cả tên trường snake_case và camelCase để tương thích bản frontend cũ.
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RecommendedItem(
        String id, String title, Integer year, double rating, String runtime, List<String> genres,
        String description, String poster, String backdrop, String videoUrl, Integer matchPercentage,
        @JsonProperty("item_type") String itemType,
        @JsonProperty("series_id") String seriesId,
        @JsonProperty("season_id") String seasonId,
        @JsonProperty("episode_id") String catalogEpisodeId,
        @JsonProperty("segment_id") String segmentId,
        @JsonProperty("episode_number") Integer episodeNumber,
        @JsonProperty("air_date") String airDate,
        @JsonProperty("duration_ms") Long durationMs,
        @JsonProperty("start_ms") Long catalogStartMs,
        @JsonProperty("end_ms") Long endMs,
        @JsonProperty("primary_media_asset_id") String primaryMediaAssetId,
        @JsonProperty("media_asset_id") String mediaAssetId,
        List<String> aliases, List<String> characters,
        @JsonProperty("plot_facts") List<String> plotFacts,
        List<String> keywords, List<String> themes,
        @JsonProperty("visual_summary") String visualSummary,
        @JsonProperty("dialogue_summary") String dialogueSummary,
        @JsonProperty("sound_events") List<String> soundEvents,
        @JsonProperty("series_title") String seriesTitle,
        @JsonProperty("episode_title") String episodeTitle,
        @JsonProperty("comparative_attributes") Map<String, Object> comparativeAttributes,
        Map<String, Object> diagnostics,
        String episodeId, Long startMs, Double score, Double calibratedScore, List<Evidence> evidence,
        @JsonUnwrapped RatingSummary ratings
) {
    private static final String EPISODE = "episode";

    // Dựng kết quả gợi ý theo sở thích, lấy luôn mô tả tập phim làm bằng chứng hiển thị.
    static RecommendedItem affinity(RecommendationCandidate item, double score) {
        return from(item, score,
                List.of(new Evidence(item.id(), item.description(), item.startMs(), item.endMs(), EPISODE)));
    }

    static RecommendedItem from(RecommendationCandidate item, Double score, List<Evidence> evidence) {
        return new RecommendedItem(item.id(), item.title(), item.year(), item.rating(), item.runtime(), item.genres(),
                item.description(), item.poster(), item.backdrop(), null, null, EPISODE, item.seriesId(),
                item.seasonId(), item.id(), null, item.episodeNumber(), null, item.durationMs(), item.startMs(),
                item.endMs(), null, null, null, item.characters(), null, item.keywords(), item.themes(), null, null,
                null, item.seriesTitle(), null, null, null, item.id(), item.startMs(), score, null, evidence,
                RatingSummary.EMPTY);
    }

    RecommendedItem withRatings(RatingSummary summary) {
        return new RecommendedItem(id, title, year, rating, runtime, genres, description, poster, backdrop, videoUrl,
                matchPercentage, itemType, seriesId, seasonId, catalogEpisodeId, segmentId, episodeNumber, airDate,
                durationMs, catalogStartMs, endMs, primaryMediaAssetId, mediaAssetId, aliases, characters, plotFacts,
                keywords, themes, visualSummary, dialogueSummary, soundEvents, seriesTitle, episodeTitle,
                comparativeAttributes, diagnostics, episodeId, startMs, score, calibratedScore, evidence, summary);
    }

    // Đoạn văn bản và mốc thời gian trích ra để giải thích vì sao tập phim hợp với truy vấn.
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Evidence(String documentId, String text, Long startMs, Long endMs, String itemType) {
    }
}
