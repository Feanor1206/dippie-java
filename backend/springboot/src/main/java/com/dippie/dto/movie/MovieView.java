package com.dippie.dto.movie;

import com.dippie.common.rating.RatingSummary;
import com.dippie.model.catalog.CatalogDisplayText;
import com.dippie.model.catalog.EpisodeRuntime;
import com.dippie.model.catalog.SeriesArtwork;
import com.dippie.model.movie.Movie;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// view phim trả API, kèm vị trí bộ/tập với điểm cộng đồng qua @JsonUnwrapped
public record MovieView(
        String id, String title, Integer year, double rating, String runtime, List<String> genres, String description,
        String poster, String backdrop, String videoUrl, @JsonIgnore String itemType, @JsonIgnore String seriesId,
        @JsonIgnore String seriesTitle, @JsonIgnore String episodeId, @JsonIgnore Integer episodeNumber,
        @JsonUnwrapped RatingSummary ratings
) {

    private static final String SERIES = "series";
    private static final String EPISODE = "episode";

    public MovieView {
        genres = genres == null ? List.of() : List.copyOf(genres);
    }

    // dùng @JsonAnyGetter để phim lẻ khỏi thừa key item_type, còn tập chưa đánh số vẫn xuất episode_number là null
    @JsonAnyGetter
    public Map<String, Object> catalogPlacement() {
        if (itemType == null) {
            return Map.of();
        }
        Map<String, Object> placement = new LinkedHashMap<>();
        placement.put("item_type", itemType);
        placement.put("series_id", seriesId);
        placement.put("series_title", seriesTitle);
        if (EPISODE.equals(itemType)) {
            placement.put("episode_id", episodeId);
            placement.put("episode_number", episodeNumber);
        }
        return Collections.unmodifiableMap(placement);
    }

    public static MovieView from(Movie movie) {
        return from(movie, RatingSummary.EMPTY);
    }

    public static MovieView from(Movie movie, RatingSummary ratings) {
        return create(movie, null, null, null, null, null, movie.getRuntime(), SeriesArtwork.EMPTY, ratings);
    }

    // đổi runtime "Series" cũ sang nhãn tiếng Việt từ CatalogDisplayText, bù ảnh bìa nếu bảng movies thiếu
    public static MovieView forSeries(Movie movie, String seriesTitle, SeriesArtwork artwork, RatingSummary ratings) {
        String runtime = "Series".equals(movie.getRuntime()) ? CatalogDisplayText.SERIES : movie.getRuntime();
        return create(movie, SERIES, movie.getId(), seriesTitle, null, null, runtime, artwork, ratings);
    }

    // dựng view cho một tập, tính nhãn thời lượng từ durationMs rồi giữ link về bộ phim cha
    public static MovieView forEpisode(Movie movie, String seriesId, String seriesTitle, Integer episodeNumber,
            Long durationMs, SeriesArtwork artwork, RatingSummary ratings) {
        return create(movie, EPISODE, seriesId, seriesTitle, movie.getId(), episodeNumber, EpisodeRuntime.label(durationMs),
                artwork, ratings);
    }

    private static MovieView create(Movie movie, String itemType, String seriesId, String seriesTitle,
            String episodeId, Integer episodeNumber, String runtime, SeriesArtwork fallback, RatingSummary ratings) {
        List<String> genres = Arrays.stream(Objects.requireNonNullElse(movie.getGenres(), "").split(","))
                .map(String::strip).filter(genre -> !genre.isEmpty()).toList();
        SeriesArtwork artwork = fallback.fillMissing(movie.getPoster(), movie.getBackdrop());
        return new MovieView(movie.getId(), movie.getTitle(), movie.getReleaseYear(), movie.getRating(), runtime,
                genres, movie.getDescription(), artwork.posterUrl(), artwork.backdropUrl(), movie.getVideoUrl(),
                itemType, seriesId, seriesTitle, episodeId, episodeNumber, ratings);
    }
}
