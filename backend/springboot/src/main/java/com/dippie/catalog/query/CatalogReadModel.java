package com.dippie.catalog.query;

import com.dippie.catalog.domain.CatalogSeriesView;
import com.dippie.catalog.domain.SeriesArtwork;
import com.dippie.recommendation.RecommendationCandidate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class CatalogReadModel {

    private final List<CatalogSeriesView> series;
    private final Set<String> visibleSeriesIds;
    private final Set<String> visibleEpisodeIds;
    private final Set<String> hiddenMovieIds;
    private final List<CatalogSearchService.SearchRow> searchRows;
    private final List<RecommendationCandidate> candidates;
    private final Map<String, List<SeriesEpisode>> seriesEpisodes;
    private final List<SeriesCard> seriesCards;
    private final Map<String, SeriesCard> cardsById;
    private final Map<String, SeriesEpisode> episodesById;

    CatalogReadModel(List<CatalogSeriesView> series, Set<String> visibleSeriesIds,
            Set<String> visibleEpisodeIds, Set<String> hiddenMovieIds,
            List<CatalogSearchService.SearchRow> searchRows, List<RecommendationCandidate> candidates,
            Map<String, List<SeriesEpisode>> seriesEpisodes, List<SeriesCard> seriesCards,
            Map<String, SeriesEpisode> episodesById) {
        this.series = List.copyOf(series);
        this.visibleSeriesIds = Set.copyOf(visibleSeriesIds);
        this.visibleEpisodeIds = Set.copyOf(visibleEpisodeIds);
        this.hiddenMovieIds = Set.copyOf(hiddenMovieIds);
        this.searchRows = List.copyOf(searchRows);
        this.candidates = List.copyOf(candidates);
        this.seriesEpisodes = seriesEpisodes.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, row -> List.copyOf(row.getValue())));
        this.seriesCards = List.copyOf(seriesCards);
        this.cardsById = seriesCards.stream().collect(Collectors.toUnmodifiableMap(SeriesCard::id, Function.identity()));
        this.episodesById = Map.copyOf(episodesById);
    }

    List<CatalogSeriesView> series(boolean admin) {
        return admin ? series : series.stream().filter(row -> visibleSeriesIds.contains(row.id())).toList();
    }

    public Set<String> visibleSeriesIds() {
        return visibleSeriesIds;
    }

    Set<String> visibleEpisodeIds() {
        return visibleEpisodeIds;
    }

    public Set<String> hiddenMovieIds() {
        return hiddenMovieIds;
    }

    List<CatalogSearchService.SearchRow> searchRows() {
        return searchRows;
    }

    public List<RecommendationCandidate> recommendationCandidates() {
        return candidates;
    }

    public List<SeriesEpisode> seriesEpisodes(String seriesId) {
        return seriesEpisodes.getOrDefault(seriesId, List.of());
    }

    List<SeriesCard> seriesCards() {
        return seriesCards;
    }

    public SeriesCard seriesCard(String seriesId) {
        return cardsById.get(seriesId);
    }

    public SeriesEpisode episode(String episodeId) {
        return episodesById.get(episodeId);
    }

    public String episodeThumbnail(String episodeId, String seriesId) {
        SeriesEpisode episode = episode(episodeId);
        SeriesCard series = seriesCard(seriesId);
        return Objects.toString(episode == null ? null : episode.artwork().posterUrl(),
                series == null ? null : series.posterUrl());
    }

    public record SeriesEpisode(String id, String title, Integer seasonNumber, Integer episodeNumber, Long durationMs,
            boolean memberAccessible, String seriesId, String seasonId, SeriesArtwork artwork) {
    }
}
