package com.dippie.movie;

import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.catalog.domain.CatalogMediaAssetRepository;
import com.dippie.catalog.query.CatalogReadModel;
import com.dippie.catalog.query.CatalogReadModelCache;
import com.dippie.catalog.query.SeriesCard;
import com.dippie.common.error.NotFoundException;
import com.dippie.media.MediaAsset;
import com.dippie.media.MediaAssetRepository;
import com.dippie.media.MediaAssetView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Expression;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MovieQueryService {

    private final MovieRepository movieRepository;
    private final MovieVisibility movieVisibility;
    private final CatalogReadModelCache catalog;
    private final MediaAssetRepository mediaAssetRepository;
    private final CatalogMediaAssetRepository catalogMediaAssetRepository;
    private final EntityManager entities;
    private final TransactionTemplate reads;

    public MovieQueryService(
            MovieRepository movieRepository,
            MovieVisibility movieVisibility,
            CatalogReadModelCache catalog,
            MediaAssetRepository mediaAssetRepository,
            CatalogMediaAssetRepository catalogMediaAssetRepository,
            EntityManager entities,
            PlatformTransactionManager transactions
    ) {
        this.movieRepository = movieRepository;
        this.movieVisibility = movieVisibility;
        this.catalog = catalog;
        this.mediaAssetRepository = mediaAssetRepository;
        this.catalogMediaAssetRepository = catalogMediaAssetRepository;
        this.entities = entities;
        this.reads = new TransactionTemplate(transactions);
        this.reads.setReadOnly(true);
    }

    public List<MovieView> listMovies(String search, String query, String q, String genre) {
        String effectiveSearch = MovieBrowseQuery.firstNonBlank(search, query, q);
        MovieFilter filter = MovieFilter.forSingleGenre(effectiveSearch, genre);
        CatalogReadModel model = catalog.current();
        var filters = filter.specification(null, null, model.hiddenMovieIds());
        return reads.execute(status -> entities.createQuery(orderedMovies(filters,
                MovieBrowseQuery.SortField.RATING, MovieBrowseQuery.SortDirection.DESC)).getResultList().stream()
                .map(movie -> toEnrichedView(movie, model)).toList());
    }

    public MovieBrowsePage browseMovies(MovieBrowseQuery query) {
        CatalogReadModel model = catalog.current();
        var filters = query.toFilter().specification(query.yearMin(), query.yearMax(), model.hiddenMovieIds());
        return reads.execute(status -> browsePage(query, filters, model));
    }

    private MovieBrowsePage browsePage(MovieBrowseQuery query, Specification<Movie> filters, CatalogReadModel model) {
        long totalElements = movieRepository.count(filters);
        BigInteger startBig = query.page().multiply(BigInteger.valueOf(query.size()));
        if (startBig.compareTo(BigInteger.valueOf(totalElements)) >= 0) {
            return MovieBrowsePage.of(List.of(), query.page(), query.size(), totalElements);
        }
        var page = orderedMovies(filters, query.sortField(), query.sortDirection())
                .offset(startBig.longValueExact()).fetch(query.size());
        List<MovieView> items = entities.createQuery(page).getResultList().stream()
                .map(movie -> toEnrichedView(movie, model)).toList();
        return MovieBrowsePage.of(items, query.page(), query.size(), totalElements);
    }

    private JpaCriteriaQuery<Movie> orderedMovies(Specification<Movie> filters, MovieBrowseQuery.SortField field,
            MovieBrowseQuery.SortDirection direction) {
        var builder = (HibernateCriteriaBuilder) entities.getCriteriaBuilder();
        var query = builder.createQuery(Movie.class);
        var movie = query.from(Movie.class);
        Expression<?> sort = switch (field) {
            case RATING -> movie.get("rating");
            case YEAR -> movie.get("releaseYear");
            case TITLE -> movie.get("title").cast(byte[].class);
        };
        query.where(filters.toPredicate(movie, query, builder));
        query.orderBy(direction == MovieBrowseQuery.SortDirection.DESC ? builder.desc(sort) : builder.asc(sort),
                builder.asc(movie.get("id").cast(byte[].class)));
        return query;
    }

    public MovieView getMovieById(String movieId) {
        CatalogReadModel model = catalog.current();
        Movie movie = movieVisibility.findVisibleById(movieId, model.hiddenMovieIds())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phim."));
        return toEnrichedView(movie, model);
    }

    public MediaDescriptorView getMediaDescriptor(String movieId) {
        Movie movie = movieVisibility.findVisibleById(movieId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phim với mã: " + movieId));
        List<MediaAsset> publicAssets = mediaAssetRepository.findByMovieIdAndAvailableTrueAndPublicAssetTrue(movieId);
        List<String> assetIds = publicAssets.stream().map(MediaAsset::getId).toList();
        Map<String, CatalogMediaAsset> catalogAssetsById = assetIds.isEmpty()
                ? Map.of()
                : catalogMediaAssetRepository.findAllById(assetIds).stream()
                        .collect(Collectors.toMap(CatalogMediaAsset::getId, Function.identity()));

        List<MediaAsset> allowedAssets = publicAssets.stream()
                .filter(asset -> {
                    CatalogMediaAsset catalogAsset = catalogAssetsById.get(asset.getId());
                    return catalogAsset == null || catalogAsset.isAccessibleBy(false, false);
                })
                .toList();

        MediaAssetView feature = allowedAssets.stream()
                .filter(asset -> "feature".equals(lowercase(asset.getKind())))
                .findFirst()
                .map(MediaAssetView::from)
                .orElse(null);
        MediaAssetView trailer = allowedAssets.stream()
                .filter(asset -> "trailer".equals(lowercase(asset.getKind())))
                .findFirst()
                .map(MediaAssetView::from)
                .orElse(null);

        if (trailer == null && movie.getVideoUrl() != null && !movie.getVideoUrl().isBlank()) {
            trailer = new MediaAssetView(
                    "fallback-trailer-" + movie.getId(),
                    "trailer",
                    movie.getVideoUrl(),
                    "video/mp4",
                    0L,
                    null,
                    true,
                    "legacy"
            );
        }

        return new MediaDescriptorView(
                movie.getId(),
                feature != null,
                trailer != null,
                feature,
                trailer
        );
    }

    private static MovieView toEnrichedView(Movie movie, CatalogReadModel model) {
        SeriesCard series = model.seriesCard(movie.getId());
        if (series != null) {
            return MovieView.forSeries(movie, series.title(), series.artwork());
        }
        CatalogReadModel.SeriesEpisode episode = model.episode(movie.getId());
        if (episode != null) {
            return MovieView.forEpisode(movie, episode.seriesId(), model.seriesCard(episode.seriesId()).title(),
                    episode.episodeNumber(), episode.artwork());
        }
        return MovieView.from(movie);
    }

    private static String lowercase(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
