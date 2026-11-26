package com.dippie.service.movie;

import com.dippie.common.rating.RatingLookup;
import com.dippie.common.rating.RatingSummary;
import com.dippie.common.text.TextFolding;
import com.dippie.dto.catalog.SeriesCard;
import com.dippie.dto.movie.MovieBrowsePage;
import com.dippie.dto.movie.MovieBrowseQuery;
import com.dippie.dto.movie.MovieGenreView;
import com.dippie.dto.movie.MovieView;
import com.dippie.exception.NotFoundException;
import com.dippie.model.catalog.SeriesEpisode;
import com.dippie.model.movie.Movie;
import com.dippie.model.movie.MovieFilter;
import com.dippie.repository.movie.MovieRepository;
import com.dippie.service.catalog.CatalogReadModel;
import com.dippie.service.catalog.CatalogReadModel.SearchRank;
import com.dippie.service.catalog.CatalogReadModelCache;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Expression;
import org.hibernate.query.NullPrecedence;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// kiểu Facade gom tìm kiếm, lọc phân trang, đọc thể loại rồi ghép thêm thông tin danh mục với điểm cộng đồng
@Service
public class MovieQueryService {

    private final MovieRepository movieRepository;
    private final MovieVisibility movieVisibility;
    private final CatalogReadModelCache catalog;
    private final EntityManager entities;
    private final MovieGenreDirectory genres;
    private final RatingLookup ratings;
    private final TransactionTemplate reads;

    public MovieQueryService(
            MovieRepository movieRepository,
            MovieVisibility movieVisibility,
            CatalogReadModelCache catalog,
            MovieGenreDirectory genres,
            RatingLookup ratings,
            EntityManager entities,
            PlatformTransactionManager transactions
    ) {
        this.movieRepository = movieRepository;
        this.movieVisibility = movieVisibility;
        this.catalog = catalog;
        this.genres = genres;
        this.ratings = ratings;
        this.entities = entities;
        this.reads = new TransactionTemplate(transactions);
        this.reads.setReadOnly(true);
    }

    public List<MovieView> listMovies(String search, String query, String q, String genre) {
        return listMovies(search, query, q, genre, null);
    }

    // trả phim khớp từ khóa với thể loại, chặn cứng limit ngay dưới SQL (mặc định 200, tối đa 500) kẻo kéo cả bảng lên RAM
    public List<MovieView> listMovies(String search, String query, String q, String genre, Integer limit) {
        int effectiveLimit = normalizeLimit(limit);
        String effectiveSearch = MovieBrowseQuery.firstNonBlank(search, query, q);
        MovieFilter filter = MovieFilter.forSingleGenre(effectiveSearch, genre);
        CatalogReadModel model = catalog.current();
        return reads.execute(status -> {
            var filters = filter.specification(null, null, model.hiddenMovieIds(),
                    genre == null || genre.isBlank() ? Set::of : genres.filterNames(model));
            var ordered = orderedMovies(filters, MovieBrowseQuery.SortField.RATING, MovieBrowseQuery.SortDirection.DESC,
                    false, Set.of()).fetch(effectiveLimit);
            return toEnrichedViews(entities.createQuery(ordered).getResultList(), model);
        });
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 200;
        }
        return Math.min(limit, 500);
    }

    // không có từ khóa thì phân trang thẳng dưới DB, có thì qua nhánh search bỏ dấu tiếng Việt với xếp hạng của CatalogReadModel
    public MovieBrowsePage browseMovies(MovieBrowseQuery query) {
        CatalogReadModel model = catalog.current();
        return reads.execute(status -> {
            var filters = query.genreFilter().specification(query.yearMin(), query.yearMax(), model.hiddenMovieIds(),
                    query.genreTokens().isEmpty() ? Set::of : genres.filterNames(model));
            return query.searchQuery() == null ? browsePage(query, filters, model) : searchPage(query, filters, model);
        });
    }

    public List<MovieGenreView> listGenres() {
        CatalogReadModel model = catalog.current();
        return reads.execute(status -> genres.visibleGenres(model));
    }

    private MovieBrowsePage browsePage(MovieBrowseQuery query, Specification<Movie> filters, CatalogReadModel model) {
        long totalElements = movieRepository.count(filters);
        BigInteger offset = query.page().multiply(BigInteger.valueOf(query.size()));
        if (offset.compareTo(BigInteger.valueOf(totalElements)) >= 0) {
            return MovieBrowsePage.of(List.of(), query.page(), query.size(), totalElements);
        }
        var page = orderedMovies(filters, query.sortField(), query.sortDirection(), true, Set.of())
                .offset(offset.longValueExact()).fetch(query.size());
        List<MovieView> items = toEnrichedViews(entities.createQuery(page).getResultList(), model);
        return MovieBrowsePage.of(items, query.page(), query.size(), totalElements);
    }

    private MovieBrowsePage searchPage(MovieBrowseQuery query, Specification<Movie> filters, CatalogReadModel model) {
        // lấy hạng tìm kiếm từ danh mục, nếu user tự chọn kiểu sort riêng thì gom mấy mã khớp mờ đẩy xuống sau khớp chính xác
        var ranks = model.movieSearchRanks(query.searchQuery());
        String term = folded(query.searchQuery());
        Set<String> fuzzyIds = query.explicitSort() ? ranks.entrySet().stream()
                .filter(entry -> entry.getValue().fuzzy()).map(Map.Entry::getKey).collect(Collectors.toSet()) : Set.of();
        var ordered = orderedMovies(filters, query.sortField(), query.sortDirection(), true, fuzzyIds);
        // giữ mấy phim có trong index danh mục hoặc chứa từ khóa bỏ dấu ở tiêu đề, mô tả
        var matches = entities.createQuery(ordered).getResultList().stream()
                .filter(movie -> ranks.containsKey(movie.getId())
                        || (!term.isEmpty() && (folded(movie.getTitle()).contains(term)
                                || folded(movie.getDescription()).contains(term))));
        // không chỉ định sort thì xếp theo SearchRank rồi mới cắt trang với gắn điểm đánh giá
        List<Movie> matching = (query.explicitSort() ? matches
                : matches.sorted(Comparator.comparing(movie -> ranks.getOrDefault(movie.getId(), SearchRank.EXACT))))
                .toList();
        BigInteger offset = query.page().multiply(BigInteger.valueOf(query.size()));
        List<MovieView> items = offset.compareTo(BigInteger.valueOf(matching.size())) >= 0 ? List.of()
                : toEnrichedViews(matching.stream().skip(offset.longValueExact()).limit(query.size()).toList(), model);
        return MovieBrowsePage.of(items, query.page(), query.size(), matching.size());
    }

    private static String folded(String text) {
        return TextFolding.foldNormalized(text);
    }

    // dựng query sắp xếp bằng Criteria API
    // ép title với id sang byte[] để tie-break chạy y hệt nhau giữa H2 test và MySQL, khỏi lệ thuộc collation từng DB
    private JpaCriteriaQuery<Movie> orderedMovies(Specification<Movie> filters, MovieBrowseQuery.SortField field,
            MovieBrowseQuery.SortDirection direction, boolean communityRating, Set<String> fuzzyIds) {
        var builder = (HibernateCriteriaBuilder) entities.getCriteriaBuilder();
        var query = builder.createQuery(Movie.class);
        var movie = query.from(Movie.class);
        Expression<?> sort = switch (field) {
            case RATING -> communityRating ? ratings.averageSubquery(builder, query, movie.get("id")) : movie.get("rating");
            case YEAR -> movie.get("releaseYear");
            case TITLE -> movie.get("title").cast(byte[].class);
        };
        // đẩy phim thiếu năm hay chưa có điểm xuống cuối, rồi phá hòa bằng id dạng byte[]
        query.where(filters.toPredicate(movie, query, builder));
        var order = direction == MovieBrowseQuery.SortDirection.DESC ? builder.desc(sort) : builder.asc(sort);
        if (field == MovieBrowseQuery.SortField.YEAR || (field == MovieBrowseQuery.SortField.RATING && communityRating)) {
            order.nullPrecedence(NullPrecedence.LAST);
        }
        var stableOrder = builder.asc(movie.get("id").cast(byte[].class));
        if (fuzzyIds.isEmpty()) {
            query.orderBy(order, stableOrder);
        } else {
            var matchOrder = builder.<Integer>selectCase().when(movie.get("id").in(fuzzyIds), 1).otherwise(0);
            query.orderBy(order, builder.asc(matchOrder), stableOrder);
        }
        return query;
    }

    // trả chi tiết phim đang mở kèm thông tin bộ/tập với điểm cộng đồng, đang ẩn thì 404
    public MovieView getMovieById(String movieId) {
        CatalogReadModel model = catalog.current();
        return reads.execute(status -> {
            Movie movie = movieVisibility.findVisibleById(movieId, model.hiddenMovieIds())
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy phim."));
            return toEnrichedViews(List.of(movie), model).getFirst();
        });
    }

    private List<MovieView> toEnrichedViews(List<Movie> movies, CatalogReadModel model) {
        return ratings.withSummaries(movies, Movie::getId, (movie, summary) -> toEnrichedView(movie, model, summary));
    }

    private static MovieView toEnrichedView(Movie movie, CatalogReadModel model, RatingSummary ratings) {
        SeriesCard series = model.seriesCard(movie.getId());
        if (series != null) {
            return MovieView.forSeries(movie, series.title(), series.artwork(), ratings);
        }
        SeriesEpisode episode = model.episode(movie.getId());
        if (episode != null) {
            return MovieView.forEpisode(movie, episode.seriesId(), model.seriesCard(episode.seriesId()).title(),
                    episode.episodeNumber(), episode.durationMs(), episode.artwork(), ratings);
        }
        return MovieView.from(movie, ratings);
    }
}
