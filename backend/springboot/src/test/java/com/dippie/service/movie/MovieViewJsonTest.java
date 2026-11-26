package com.dippie.service.movie;

import com.dippie.common.rating.RatingSummary;
import com.dippie.dto.movie.MovieView;
import com.dippie.model.catalog.SeriesArtwork;
import com.dippie.model.movie.Movie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Test JSON của MovieView để chắc chắn phim lẻ không lộ key thừa, phim bộ đổi nhãn runtime và tập chưa đánh số vẫn xuất episode_number là null.
class MovieViewJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void plainMovieHasNoCatalogPlacementKeys() {
        JsonNode json = mapper.valueToTree(MovieView.from(movie("coraline")));
        assertThat(json.has("item_type")).isFalse();
        assertThat(json.has("series_id")).isFalse();
        assertThat(json.has("series_title")).isFalse();
        assertThat(json.has("episode_id")).isFalse();
        assertThat(json.has("episode_number")).isFalse();
        assertThat(json.has("itemType")).isFalse();
    }

    @Test
    void seriesCarriesTypeIdAndTitle() {
        Movie saved = new Movie("ser_jadoo", "Jadoo", 2011, 0, "Series", "Animation", null, null, null, null);
        JsonNode json = mapper.valueToTree(MovieView.forSeries(saved, "Jadoo", SeriesArtwork.EMPTY, RatingSummary.EMPTY));
        assertThat(json.get("item_type").asText()).isEqualTo("series");
        assertThat(json.get("runtime").asText()).isEqualTo("Phim bộ");
        assertThat(json.get("series_id").asText()).isEqualTo("ser_jadoo");
        assertThat(json.get("series_title").asText()).isEqualTo("Jadoo");
        assertThat(json.has("seriesTitle")).isFalse();
        assertThat(json.has("episode_id")).isFalse();
        assertThat(json.has("episode_number")).isFalse();
    }

    @Test
    void unnumberedEpisodeEmitsExplicitNullEpisodeNumber() {
        JsonNode json = mapper.valueToTree(MovieView.forEpisode(movie("ep_special"), "ser_jadoo", "Jadoo", null,
                420_000L, SeriesArtwork.EMPTY, RatingSummary.EMPTY));
        assertThat(json.get("item_type").asText()).isEqualTo("episode");
        assertThat(json.get("episode_id").asText()).isEqualTo("ep_special");
        assertThat(json.get("series_title").asText()).isEqualTo("Jadoo");
        assertThat(json.has("episode_number")).isTrue();
        assertThat(json.get("episode_number").isNull()).isTrue();
    }

    @Test
    void existingPosterTakesPrecedenceOverSeriesArtwork() {
        Movie movie = new Movie("ser_jadoo", "Movie Title", 2026, 8.0, "7m", "Animation", "Synopsis",
                "https://example.com/existing.jpg", null, null);
        SeriesArtwork fallback = new SeriesArtwork("https://example.com/poster.jpg", "https://example.com/backdrop.jpg");
        MovieView view = MovieView.forSeries(movie, "Jadoo", fallback, RatingSummary.EMPTY);
        assertThat(view.poster()).isEqualTo("https://example.com/existing.jpg");
        assertThat(view.backdrop()).isEqualTo(fallback.backdropUrl());
    }

    private static Movie movie(String id) {
        return new Movie(id, "Title", 2026, 8.0, "7m", "Animation, Comedy", "Synopsis", null, null, null);
    }
}
