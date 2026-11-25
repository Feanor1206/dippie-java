package com.dippie.movie;

import com.dippie.catalog.domain.CatalogDisplayText;
import com.dippie.catalog.domain.EpisodeRuntime;
import com.dippie.catalog.domain.SeriesArtwork;
import com.dippie.common.rating.RatingSummary;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// DTO trả thông tin phim ra API, gắn thêm vị trí trong bộ phim/tập phim và điểm cộng đồng qua @JsonUnwrapped.
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

    // Dùng @JsonAnyGetter thay vì xuất thẳng các field record để phim lẻ không có khóa item_type thừa trong JSON, còn tập phim chưa đánh số vẫn xuất episode_number là null.
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

    // Đổi chuỗi runtime "Series" kiểu cũ sang nhãn tiếng Việt từ CatalogDisplayText và bù ảnh bìa nếu bảng movies chưa có.
    public static MovieView forSeries(Movie movie, String seriesTitle, SeriesArtwork artwork, RatingSummary ratings) {
        String runtime = "Series".equals(movie.getRuntime()) ? CatalogDisplayText.SERIES : movie.getRuntime();
        return create(movie, SERIES, movie.getId(), seriesTitle, null, null, runtime, artwork, ratings);
    }

    // Dựng DTO cho một tập phim, tính nhãn thời lượng từ durationMs và giữ lại liên kết về bộ phim cha.
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
