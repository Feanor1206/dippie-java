package com.dippie.catalog.importing;

import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.media.MediaAsset;
import com.dippie.movie.Movie;
import com.dippie.movie.MovieGenre;
import com.dippie.movie.MovieGenreTerm;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.dippie.catalog.importing.CatalogCollection.EPISODES;
import static com.dippie.catalog.importing.CatalogCollection.MEDIA_ASSETS;
import static com.dippie.catalog.importing.CatalogCollection.SERIES;

@Repository
class CatalogLegacyRows {

    private static final int DEFAULT_YEAR = 2026;
    private static final long DEFAULT_EPISODE_LENGTH_MS = 1_200_000;
    private static final String ANIMATION = "animation";

    private final EntityManager entities;

    CatalogLegacyRows(EntityManager entities) {
        this.entities = entities;
    }

    Map<String, Movie> loadMovies(JsonNode document) {
        Set<String> ids = new HashSet<>();
        SERIES.items(document).forEach(item -> ids.add(item.path("id").textValue()));
        EPISODES.items(document).forEach(item -> ids.add(item.path("id").textValue()));
        MEDIA_ASSETS.items(document).forEach(item -> ids.add(assetMovieId(item)));
        Map<String, Movie> movies = new HashMap<>();
        if (!ids.isEmpty()) {
            entities.createQuery("select movie from Movie movie where movie.id in :ids", Movie.class)
                    .setParameter("ids", ids).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList()
                    .forEach(movie -> movies.put(movie.getId(), movie));
        }
        return movies;
    }

    void syncSeries(List<CatalogSeries> series, Map<String, Movie> movies) {
        for (CatalogSeries row : series) {
            Movie movie = movies.computeIfAbsent(row.getId(), id -> createMovie(id, row.getTitle(), "Series"));
            movie.updateCatalogSeries(row.getTitle(), row.getReleaseYearStart(), row.getSynopsis());
        }
    }

    void syncEpisodes(List<CatalogEpisode> episodes, Map<String, Movie> movies) {
        for (CatalogEpisode episode : episodes) {
            Long duration = episode.getDurationMs();
            long length = duration == null || duration == 0 ? DEFAULT_EPISODE_LENGTH_MS : duration;
            String runtime = length / 60_000 + "m";
            Movie movie = movies.computeIfAbsent(episode.getId(), id -> createMovie(id, episode.getTitle(), runtime));
            movie.updateCatalogEpisode(episode.getTitle(), runtime, episode.getSynopsis());
        }
    }

    void syncAssets(JsonNode items, List<CatalogMediaAsset> assets, Map<String, Movie> movies) {
        if (assets.isEmpty()) {
            return;
        }
        Map<String, MediaAsset> legacy = new HashMap<>();
        List<String> ids = assets.stream().map(CatalogMediaAsset::getId).toList();
        entities.createQuery("select asset from MediaAsset asset where asset.id in :ids", MediaAsset.class)
                .setParameter("ids", ids).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList()
                .forEach(asset -> legacy.put(asset.getId(), asset));

        for (int index = 0; index < assets.size(); index++) {
            CatalogMediaAsset asset = assets.get(index);
            String id = asset.getId();
            String movieId = assetMovieId(items.get(index));
            movies.computeIfAbsent(movieId, key -> createMovie(key, "Phim liên kết " + id, null));
        }
        entities.flush();
        for (int index = 0; index < assets.size(); index++) {
            CatalogMediaAsset asset = assets.get(index);
            String id = asset.getId();
            Long duration = asset.getDurationMs();
            MediaAsset imported = new MediaAsset(id, assetMovieId(items.get(index)), asset.getRole(),
                    asset.getStorageProvider(), asset.getStorageKey(), asset.getRelativePath(), "v1_" + id,
                    mediaType(asset), asset.getSizeBytes(), duration == null || duration == 0 ? null : duration / 1000.0,
                    "public".equals(asset.getAccessPolicy()), asset.isEnabled());
            MediaAsset existing = legacy.get(id);
            if (existing == null) {
                entities.persist(imported);
            } else {
                existing.applyCatalogImport(imported);
            }
        }
    }

    private Movie createMovie(String id, String title, String runtime) {
        Movie movie = new Movie(id, title, DEFAULT_YEAR, 0, runtime, "Animation", null, null, null, null);
        entities.persist(movie);
        if (entities.find(MovieGenreTerm.class, ANIMATION) == null) {
            entities.persist(new MovieGenreTerm(ANIMATION));
        }
        entities.persist(new MovieGenre(id, ANIMATION));
        return movie;
    }

    private static String assetMovieId(JsonNode asset) {
        JsonNode coverage = asset.path("coverage");
        return coverage.isEmpty() ? asset.path("id").textValue() : coverage.get(0).path("episode_id").textValue();
    }

    private static String mediaType(CatalogMediaAsset asset) {
        String type = asset.getContentType();
        String extension = StringUtils.getFilenameExtension(asset.getRelativePath());
        String guessed = switch (extension == null ? "" : extension.toLowerCase(Locale.ROOT)) {
            case "aac", "adts" -> "audio/aac";
            case "m4v" -> "video/mp4";
            default -> MediaTypeFactory.getMediaType(asset.getRelativePath()).map(MediaType::toString).orElse("");
        };
        return guessed.startsWith(type + "/") ? guessed : type.equals("audio") ? "audio/mpeg" : "video/mp4";
    }
}
