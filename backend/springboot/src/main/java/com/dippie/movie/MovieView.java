package com.dippie.movie;

import com.dippie.catalog.domain.SeriesArtwork;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record MovieView(
        String id,
        String title,
        int year,
        double rating,
        String runtime,
        List<String> genres,
        String description,
        String poster,
        String backdrop,
        String videoUrl,
        @JsonIgnore String itemType,
        @JsonIgnore String seriesId,
        @JsonIgnore String seriesTitle,
        @JsonIgnore String episodeId,
        @JsonIgnore Integer episodeNumber
) {

    private static final String SERIES = "series";
    private static final String EPISODE = "episode";

    public MovieView {
        genres = genres == null ? List.of() : List.copyOf(genres);
    }

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
        return create(movie, null, null, null, null, null, SeriesArtwork.EMPTY);
    }

    public static MovieView forSeries(Movie movie, String seriesTitle, SeriesArtwork artwork) {
        return create(movie, SERIES, movie.getId(), seriesTitle, null, null, artwork);
    }

    public static MovieView forEpisode(Movie movie, String seriesId, String seriesTitle, Integer episodeNumber,
            SeriesArtwork artwork) {
        return create(movie, EPISODE, seriesId, seriesTitle, movie.getId(), episodeNumber, artwork);
    }

    private static MovieView create(
            Movie movie,
            String itemType,
            String seriesId,
            String seriesTitle,
            String episodeId,
            Integer episodeNumber,
            SeriesArtwork fallback
    ) {
        List<String> genres = Arrays.stream(Objects.requireNonNullElse(movie.getGenres(), "").split(","))
                .map(String::strip)
                .filter(genre -> !genre.isEmpty())
                .toList();
        SeriesArtwork artwork = fallback.fillMissing(movie.getPoster(), movie.getBackdrop());
        return new MovieView(
                movie.getId(),
                movie.getTitle(),
                movie.getReleaseYear(),
                movie.getRating(),
                movie.getRuntime(),
                genres,
                movie.getDescription(),
                artwork.posterUrl(),
                artwork.backdropUrl(),
                movie.getVideoUrl(),
                itemType,
                seriesId,
                seriesTitle,
                episodeId,
                episodeNumber);
    }
}
