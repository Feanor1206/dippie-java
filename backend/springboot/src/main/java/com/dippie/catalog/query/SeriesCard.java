package com.dippie.catalog.query;

import com.dippie.catalog.domain.CatalogGenre;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.domain.SeriesArtwork;
import com.dippie.catalog.query.CatalogReadModel.SeriesEpisode;
import com.dippie.common.json.JsonText;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SeriesCard(String id, String title, String originalTitle, String synopsis, Integer releaseYearStart,
        Integer releaseYearEnd, List<Genre> genres, int seasonCount, int episodeCount, String posterUrl,
        String backdropUrl) {

    private static final Comparator<Integer> NUMBER_ORDER = Comparator.nullsLast(Comparator.naturalOrder());
    private static final Comparator<SeriesEpisode> EPISODE_ORDER = Comparator
            .comparing(SeriesEpisode::seasonNumber, NUMBER_ORDER)
            .thenComparing(SeriesEpisode::episodeNumber, NUMBER_ORDER)
            .thenComparing(SeriesEpisode::id);

    static SeriesCard from(CatalogSeries series, List<SeriesEpisode> episodes, Map<String, Genre> genresById) {
        List<Genre> genres = JsonText.stringList(series.getGenreIdsJson()).stream()
                .map(genresById::get).filter(Objects::nonNull).toList();
        int seasonCount = Math.toIntExact(episodes.stream().map(SeriesEpisode::seasonId).filter(Objects::nonNull)
                .distinct().count());
        SeriesArtwork youtube = episodes.stream().filter(episode -> episode.artwork().posterUrl() != null)
                .min(EPISODE_ORDER).map(SeriesEpisode::artwork).orElse(SeriesArtwork.EMPTY);
        SeriesArtwork artwork = SeriesArtwork.fromSeries(series.getArtworksJson(), youtube);
        return new SeriesCard(series.getId(), series.getTitle(), series.getOriginalTitle(), series.getSynopsis(),
                series.getReleaseYearStart(), series.getReleaseYearEnd(), genres, seasonCount, episodes.size(),
                artwork.posterUrl(), artwork.backdropUrl());
    }

    public SeriesArtwork artwork() {
        return new SeriesArtwork(posterUrl, backdropUrl);
    }

    public record Genre(String id, String label) {
        static Genre from(CatalogGenre genre) {
            return new Genre(genre.getId(), JsonText.parse(genre.getLabelsJson()).map(Genre::preferredLabel)
                    .orElse(genre.getId()));
        }

        private static String preferredLabel(JsonNode labels) {
            if (labels.isObject()) {
                String vietnamese = labels.path("vi").textValue();
                return vietnamese != null && !vietnamese.isBlank() ? vietnamese : labels.valueStream()
                        .map(JsonNode::textValue).filter(Objects::nonNull).filter(text -> !text.isBlank())
                        .findFirst().orElse(null);
            }
            String first = null;
            for (JsonNode label : labels) {
                String text = label.path("text").textValue();
                if (text != null && !text.isBlank()) {
                    if ("vi".equals(label.path("language").textValue())) {
                        return text;
                    }
                    if (first == null) {
                        first = text;
                    }
                }
            }
            return first;
        }
    }
}
