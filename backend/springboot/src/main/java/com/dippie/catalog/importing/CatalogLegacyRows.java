package com.dippie.catalog.importing;

import com.dippie.catalog.domain.CatalogDisplayText;
import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.catalog.domain.CatalogSeason;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.domain.EpisodeRuntime;
import com.dippie.common.json.JsonText;
import com.dippie.media.MediaAsset;
import com.dippie.movie.Movie;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.dippie.catalog.importing.CatalogCollection.EPISODES;
import static com.dippie.catalog.importing.CatalogCollection.MEDIA_ASSETS;
import static com.dippie.catalog.importing.CatalogCollection.SEASONS;
import static com.dippie.catalog.importing.CatalogCollection.SERIES;

// Đồng bộ dữ liệu từ các bảng catalog mới sang bảng cũ movies và media_assets để các trang duyệt phim và phát video cũ vẫn chạy ổn.
@Repository
class CatalogLegacyRows {

    private static final String ANIMATION = "animation";

    private final EntityManager entities;
    private final CatalogMovieGenres movieGenres;

    CatalogLegacyRows(EntityManager entities, CatalogMovieGenres movieGenres) {
        this.entities = entities;
        this.movieGenres = movieGenres;
    }

    // Nạp trước và khóa các hàng trong bảng movies cho tất cả bộ phim, tập và tệp chuẩn bị cập nhật.
    Map<String, Movie> loadMovies(JsonNode document) {
        Set<String> ids = new HashSet<>();
        SERIES.items(document).forEach(item -> ids.add(item.path("id").textValue()));
        EPISODES.items(document).forEach(item -> ids.add(item.path("id").textValue()));
        MEDIA_ASSETS.items(document).forEach(item -> ids.add(assetMovieId(item)));
        Map<String, Movie> movies = new HashMap<>();
        loadMovies(ids, movies);
        return movies;
    }

    private void loadMovies(Set<String> ids, Map<String, Movie> movies) {
        if (!ids.isEmpty()) {
            entities.createQuery("select movie from Movie movie where movie.id in :ids", Movie.class)
                    .setParameter("ids", ids).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList()
                    .forEach(movie -> movies.put(movie.getId(), movie));
        }
    }

    // Cập nhật tiêu đề, năm phát hành, thời lượng, mô tả và thể loại cho mọi hàng Movie bị ảnh hưởng bởi đợt nhập.
    void syncMetadata(JsonNode document, Map<String, Movie> movies) {
        var seriesIds = ids(SERIES, document);
        var seasonIds = ids(SEASONS, document);
        var episodeIds = ids(EPISODES, document);
        if (seriesIds.isEmpty() && seasonIds.isEmpty() && episodeIds.isEmpty()) {
            return;
        }

        // Khi bộ phim hoặc mùa đổi thông tin thì kéo cả các tập con để tính lại năm phát hành và thể loại kế thừa.
        var series = seriesIds.isEmpty() ? List.<CatalogSeries>of()
                : entities.createQuery("select series from CatalogSeries series where series.id in :ids",
                        CatalogSeries.class).setParameter("ids", seriesIds).getResultList();
        var episodes = entities.createQuery("""
                select new com.dippie.catalog.importing.CatalogLegacyRows$EpisodeRow(episode, series, season)
                from CatalogEpisode episode
                join CatalogSeries series on series.id = episode.seriesId
                left join CatalogSeason season on season.id = episode.seasonId
                where episode.id in :episodes or episode.seriesId in :series or episode.seasonId in :seasons
                """, EpisodeRow.class).setParameter("episodes", episodeIds).setParameter("series", seriesIds)
                .setParameter("seasons", seasonIds).getResultList();
        Set<String> missing = new HashSet<>();
        episodes.stream().map(row -> row.episode().getId()).filter(id -> !movies.containsKey(id)).forEach(missing::add);
        loadMovies(missing, movies);

        // Ghi metadata mới vào từng hàng Movie rồi đồng bộ lại bảng nối movie_genres.
        Map<String, Set<String>> genres = new LinkedHashMap<>();
        for (CatalogSeries row : series) {
            applyMetadata(movies, genres, row.getId(), row.getTitle(), row.getReleaseYearStart(), CatalogDisplayText.SERIES,
                    row.getSynopsis(), genreIds(row, null));
        }
        for (EpisodeRow row : episodes) {
            CatalogEpisode episode = row.episode();
            applyMetadata(movies, genres, episode.getId(), episode.getTitle(),
                    episode.releaseYear(row.season(), row.series()), EpisodeRuntime.label(episode.getDurationMs()),
                    episode.getSynopsis(),
                    genreIds(row.series(), episode));
        }
        movieGenres.sync(genres);
    }

    private void applyMetadata(Map<String, Movie> movies, Map<String, Set<String>> genres, String id,
            String title, Integer year, String runtime, String synopsis, Set<String> genreIds) {
        Movie movie = movies.computeIfAbsent(id, key -> createMovie(key, title));
        movie.updateCatalogMetadata(title, year, runtime, synopsis, String.join(",", genreIds));
        genres.put(id, genreIds);
    }

    private static Set<String> genreIds(CatalogSeries series, CatalogEpisode episode) {
        Set<String> genres = new LinkedHashSet<>();
        genres.add(ANIMATION);
        genres.addAll(JsonText.stringList(series.getGenreIdsJson()));
        if (episode != null) {
            genres.addAll(JsonText.stringList(episode.getGenreIdsJson()));
        }
        return genres;
    }

    private static Set<String> ids(CatalogCollection collection, JsonNode document) {
        Set<String> ids = new HashSet<>();
        collection.items(document).forEach(item -> ids.add(item.path("id").textValue()));
        return ids;
    }

    // Đồng bộ danh sách CatalogMediaAsset sang bảng media_assets cũ; nếu phim đích chưa có trong movies thì tạo tạm một hàng giữ khóa ngoại.
    void syncAssets(JsonNode items, List<CatalogMediaAsset> assets, Map<String, Movie> movies) {
        if (assets.isEmpty()) {
            return;
        }
        Map<String, MediaAsset> legacy = new HashMap<>();
        List<String> ids = assets.stream().map(CatalogMediaAsset::getId).toList();
        entities.createQuery("select asset from MediaAsset asset where asset.id in :ids", MediaAsset.class)
                .setParameter("ids", ids).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList()
                .forEach(asset -> legacy.put(asset.getId(), asset));

        // Phải tạo và flush đủ các hàng Movie liên kết trước vì media_assets.movie_id có ràng buộc khóa ngoại.
        Map<String, Set<String>> linkedMovieGenres = new HashMap<>();
        for (int index = 0; index < assets.size(); index++) {
            String id = assets.get(index).getId();
            String movieId = assetMovieId(items.get(index));
            if (!movies.containsKey(movieId)) {
                Movie movie = createMovie(movieId, "Phim liên kết " + id);
                movie.updateCatalogMetadata(movie.getTitle(), null, null, null, ANIMATION);
                movies.put(movieId, movie);
                linkedMovieGenres.put(movieId, Set.of(ANIMATION));
            }
        }
        movieGenres.sync(linkedMovieGenres);
        entities.flush();

        // Suy đoán MIME từ đuôi tệp rồi chèn mới hoặc cập nhật từng bản ghi MediaAsset cũ.
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

    private Movie createMovie(String id, String title) {
        Movie movie = new Movie(id, title, null, 0, null, null, null, null, null, null);
        entities.persist(movie);
        return movie;
    }

    // Record hứng kết quả JPQL gom tập phim kèm bộ và mùa cha để suy ra năm phát hành.
    private record EpisodeRow(CatalogEpisode episode, CatalogSeries series, CatalogSeason season) {
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
