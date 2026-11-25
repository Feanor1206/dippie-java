package com.dippie.catalog.query;

import com.dippie.catalog.domain.CatalogCharacter;
import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogGenre;
import com.dippie.catalog.domain.CatalogSeason;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.domain.SeriesArtwork;
import com.dippie.common.json.JsonText;
import com.dippie.movie.Movie;
import com.dippie.recommendation.RecommendationCandidate;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class CatalogRecommendationRows {

    private final EntityManager entities;

    CatalogRecommendationRows(EntityManager entities) {
        this.entities = entities;
    }

    List<RecommendationCandidate> load(List<CatalogEpisode> episodes, Map<String, CatalogSeries> series,
            Map<String, CatalogSeason> seasons, Predicate<CatalogEpisode> isEligible,
            Function<String, SeriesArtwork> artworkById) {
        var eligible = episodes.stream().filter(isEligible).toList();
        if (eligible.isEmpty()) {
            return List.of();
        }
        Map<String, Movie> movies = entities.createQuery("""
                select movie from Movie movie join CatalogEpisode episode on episode.id = movie.id
                where episode.publicationStatus = 'ready'
                """, Movie.class).getResultList().stream().collect(Collectors.toMap(Movie::getId, movie -> movie));

        var episodeGenres = genres("""
                select link.episodeId, genre from CatalogEpisodeGenre link
                join CatalogGenre genre on genre.id = link.genreId order by link.genreId
                """);
        var seriesGenres = genres("""
                select link.seriesId, genre from CatalogSeriesGenre link
                join CatalogGenre genre on genre.id = link.genreId order by link.genreId
                """);
        var characters = characters();
        return eligible.stream()
                .map(episode -> new EpisodeRow(episode, series.get(episode.getSeriesId()),
                        episode.getSeasonId() == null ? null : seasons.get(episode.getSeasonId()),
                        movies.get(episode.getId())))
                .map(row -> row.candidate(
                        episodeGenres.getOrDefault(row.episode().getId(),
                                seriesGenres.getOrDefault(row.series().getId(), Set.of())),
                        characters.getOrDefault(row.episode().getId(), Set.of()), artworkById.apply(row.episode().getId())))
                .toList();
    }

    private Map<String, Set<String>> genres(String query) {
        Map<String, Set<String>> values = new HashMap<>();
        for (Object[] row : entities.createQuery(query, Object[].class).getResultList()) {
            CatalogGenre genre = (CatalogGenre) row[1];
            Set<String> tags = values.computeIfAbsent((String) row[0], id -> new LinkedHashSet<>());
            tags.add(genre.getId());
            jsonArray(genre.getLabelsJson()).map(label -> label.path("text").textValue())
                    .filter(label -> label != null).forEach(tags::add);
            aliases(genre.getAliasesJson()).forEach(tags::add);
        }
        return values;
    }

    private Map<String, Set<String>> characters() {
        Map<String, Set<String>> values = new HashMap<>();
        for (Object[] row : entities.createQuery("""
                select link.episodeId, character from CatalogEpisodeCharacter link
                join CatalogCharacter character on character.id = link.characterId order by link.characterId
                """, Object[].class).getResultList()) {
            CatalogCharacter character = (CatalogCharacter) row[1];
            Set<String> names = values.computeIfAbsent((String) row[0], id -> new LinkedHashSet<>());
            names.add(character.getName());
            aliases(character.getAliasesJson()).forEach(names::add);
        }
        return values;
    }

    private static Stream<JsonNode> jsonArray(String text) {
        return JsonText.parse(text).filter(JsonNode::isArray).stream().flatMap(JsonNode::valueStream);
    }

    private static Stream<String> aliases(String text) {
        return jsonArray(text).map(alias -> alias.isTextual() ? alias.textValue() : alias.path("text").textValue())
                .filter(alias -> alias != null && !alias.isBlank())
                .map(String::strip);
    }

    private record EpisodeRow(CatalogEpisode episode, CatalogSeries series, CatalogSeason season, Movie movie) {
        RecommendationCandidate candidate(Set<String> genres, Set<String> characters, SeriesArtwork fallback) {
            Long duration = episode.getDurationMs();
            long minutes = duration == null ? 0 : duration / 60_000;
            String description = episode.getSynopsis();
            if (description == null || description.isEmpty()) {
                String number = episode.getNumber() == null ? "None" : episode.getNumber().toString();
                description = "Episode " + number + ": " + episode.getTitle();
            }
            SeriesArtwork artwork = movie == null ? fallback : fallback.fillMissing(movie.getPoster(), movie.getBackdrop());
            return new RecommendationCandidate(episode.getId(), episode.getTitle(), episode.getSeriesId(),
                    episode.getSeasonId(), episode.getNumber(), year(), movie == null ? 0 : movie.getRating(),
                    minutes > 0 ? minutes + "m" : "Episode", artwork.posterUrl(), artwork.backdropUrl(),
                    description, List.copyOf(genres),
                    JsonText.stringList(episode.getThemesJson()), JsonText.stringList(episode.getKeywordsJson()),
                    List.copyOf(characters), null, null, duration, series.getTitle());
        }

        private int year() {
            if (episode.getReleaseDate() != null) {
                return episode.getReleaseDate().getYear();
            }
            if (season != null) {
                int seasonYear = JsonText.parse(season.getRawJson())
                        .map(row -> row.path("release_year_start").asInt(0)).orElse(0);
                if (seasonYear != 0) {
                    return seasonYear;
                }
            }
            return series.getReleaseYearStart() == null ? 0 : series.getReleaseYearStart();
        }
    }
}
