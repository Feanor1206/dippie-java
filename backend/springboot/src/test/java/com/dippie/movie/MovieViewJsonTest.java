package com.dippie.movie;

import static org.assertj.core.api.Assertions.assertThat;

import com.dippie.catalog.domain.SeriesArtwork;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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
        JsonNode json = mapper.valueToTree(MovieView.forSeries(movie("ser_jadoo"), "Jadoo", SeriesArtwork.EMPTY));
        assertThat(json.get("item_type").asText()).isEqualTo("series");
        assertThat(json.get("series_id").asText()).isEqualTo("ser_jadoo");
        assertThat(json.get("series_title").asText()).isEqualTo("Jadoo");
        assertThat(json.has("seriesTitle")).isFalse();
        assertThat(json.has("episode_id")).isFalse();
        assertThat(json.has("episode_number")).isFalse();
    }

    @Test
    void unnumberedEpisodeEmitsExplicitNullEpisodeNumber() {
        JsonNode json = mapper.valueToTree(MovieView.forEpisode(movie("ep_special"), "ser_jadoo", "Jadoo", null,
                SeriesArtwork.EMPTY));
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
        MovieView view = MovieView.forSeries(movie, "Jadoo", fallback);
        assertThat(view.poster()).isEqualTo("https://example.com/existing.jpg");
        assertThat(view.backdrop()).isEqualTo(fallback.backdropUrl());
    }

    private static Movie movie(String id) {
        return new Movie(id, "Title", 2026, 8.0, "7m", "Animation, Comedy", "Synopsis", null, null, null);
    }
}
