package com.dippie.movie;

import com.dippie.catalog.query.CatalogReadModel;
import com.dippie.catalog.query.CatalogReadModel.SearchRank;
import com.dippie.catalog.query.CatalogReadModelCache;
import com.dippie.catalog.query.SeriesCard;
import com.dippie.common.error.NotFoundException;
import com.dippie.common.rating.RatingLookup;
import com.dippie.common.rating.RatingSummary;
import com.dippie.common.text.TextFolding;
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

// Facade gom toàn bộ luồng tìm kiếm, lọc phân trang, đọc thể loại và ghép thêm thông tin danh mục cùng điểm cộng đồng cho phim.
@Service
public class MovieQueryService {

    private static final int DEFAULT_LIST_LIMIT = 200;
    private static final int MAX_LIST_LIMIT = 500;

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

    // Trả danh sách phim khớp từ khóa và thể loại, chặn cứng số bản ghi ngay ở SQL (mặc định 200, tối đa 500) để không kéo cả bảng lên RAM.
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
            return DEFAULT_LIST_LIMIT;
        }
        return Math.min(limit, MAX_LIST_LIMIT);
    }

    // Nếu không có từ khóa thì phân trang thẳng dưới DB; có từ khóa thì đi qua nhánh tìm kiếm bỏ dấu tiếng Việt và xếp hạng của CatalogReadModel.
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
        // Lấy thứ hạng tìm kiếm từ danh mục; nếu người dùng chọn kiểu sắp xếp riêng thì gom các mã khớp mờ để đẩy xuống sau kết quả khớp chính xác
        var ranks = model.movieSearchRanks(query.searchQuery());
        String term = folded(query.searchQuery());
        Set<String> fuzzyIds = query.explicitSort() ? ranks.entrySet().stream()
                .filter(entry -> entry.getValue().fuzzy()).map(Map.Entry::getKey).collect(Collectors.toSet()) : Set.of();
        var ordered = orderedMovies(filters, query.sortField(), query.sortDirection(), true, fuzzyIds);
        // Lọc các phim có trong chỉ mục danh mục hoặc chứa từ khóa đã bỏ dấu trong tiêu đề, mô tả
        var matches = entities.createQuery(ordered).getResultList().stream()
                .filter(movie -> ranks.containsKey(movie.getId())
                        || (!term.isEmpty() && (folded(movie.getTitle()).contains(term)
                                || folded(movie.getDescription()).contains(term))));
        // Nếu không chỉ định sort thì sắp theo độ liên quan SearchRank rồi mới cắt trang và gắn điểm đánh giá
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

    // Dựng truy vấn sắp xếp bằng Criteria API.
    // Mình ép title và id sang byte[] để thứ tự tie-break chạy giống nhau giữa H2 test và MySQL, không bị phụ thuộc collation của từng DB.
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
        // Đẩy bản ghi thiếu năm hoặc chưa có đánh giá xuống cuối, sau đó phá hòa bằng id nhị phân
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

    // Trả chi tiết một phim đang hiển thị kèm thông tin loạt phim/tập phim và điểm cộng đồng, ẩn thì báo 404.
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
        CatalogReadModel.SeriesEpisode episode = model.episode(movie.getId());
        if (episode != null) {
            return MovieView.forEpisode(movie, episode.seriesId(), model.seriesCard(episode.seriesId()).title(),
                    episode.episodeNumber(), episode.durationMs(), episode.artwork(), ratings);
        }
        return MovieView.from(movie, ratings);
    }
}
