package com.dippie.model.movie;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

// Bộ lọc dùng chung cho cả in-memory (Predicate<Movie>) lẫn câu SQL (Specification<Movie>).
// Phần SQL tự loại bỏ hiddenIds để phim nháp hoặc bị ẩn không bao giờ lọt ra ngoài.
public final class MovieFilter implements java.util.function.Predicate<Movie> {

    private static final String TOKEN_WHITESPACE = "[\\x{9}-\\x{D}\\x{1C}-\\x{20}\\x{1680}\\x{2000}-\\x{2006}"
            + "\\x{2008}-\\x{200A}\\x{2028}\\x{2029}\\x{205F}\\x{3000}]*";

    private final String searchTerm;
    private final List<String> genreTokens;

    public MovieFilter(String rawQuery, List<String> rawGenreTokens) {
        this.searchTerm = normalizeTerm(rawQuery);
        this.genreTokens = rawGenreTokens == null
                ? List.of()
                : rawGenreTokens.stream()
                        .map(MovieFilter::normalizeTerm)
                        .filter(token -> token != null)
                        .distinct()
                        .toList();
    }

    public static MovieFilter forSingleGenre(String rawQuery, String rawGenre) {
        List<String> tokens = rawGenre == null ? List.of() : List.of(rawGenre);
        return new MovieFilter(rawQuery, tokens);
    }

    // Sinh JPA Specification lọc theo năm, bỏ phim ẩn, khớp từ khóa và khớp thể loại ngay dưới DB.
    // Mình dùng regexp_like kèm Pattern.quote thay vì LIKE để mấy ký tự như "100%" không biến thành wildcard SQL.
    public Specification<Movie> specification(Integer yearMin, Integer yearMax, Set<String> hiddenIds,
            Function<String, Set<String>> genreNames) {
        return (movie, query, builder) -> {
            List<Predicate> filters = new ArrayList<>();
            // Chặn theo khoảng năm phát hành và loại hết phim ẩn/bản nháp
            if (yearMin != null) {
                filters.add(builder.ge(movie.get("releaseYear"), yearMin));
            }
            if (yearMax != null) {
                filters.add(builder.le(movie.get("releaseYear"), yearMax));
            }
            if (!hiddenIds.isEmpty()) {
                filters.add(builder.not(movie.get("id").in(hiddenIds)));
            }
            // So khớp từ khóa theo chuỗi nguyên văn trên tiêu đề hoặc mô tả
            if (searchTerm != null) {
                String literal = Pattern.quote(searchTerm);
                filters.add(builder.or(matchesPattern(builder, movie.get("title"), literal),
                        matchesPattern(builder, movie.get("description"), literal)));
            }
            if (!genreTokens.isEmpty()) {
                filters.add(genres(movie, query, builder, genreNames));
            }
            return builder.and(filters.toArray(Predicate[]::new));
        };
    }

    // Việt để ý nhánh dự phòng chỗ này: nếu phim đã có dòng trong bảng movie_genres thì chỉ tin bảng liên kết, chỉ rơi về cột CSV movies.genres khi phim chưa được tách bảng.
    private Predicate genres(Root<Movie> movie, CriteriaQuery<?> query, CriteriaBuilder builder,
            Function<String, Set<String>> genreNames) {
        List<Predicate> linked = new ArrayList<>();
        List<Predicate> csv = new ArrayList<>();
        csv.add(builder.not(hasGenreLink(movie, query, builder, null)));
        for (String token : genreTokens) {
            Set<String> names = genreNames.apply(token);
            linked.add(hasGenreLink(movie, query, builder, names));
            csv.add(builder.or(names.stream().filter(name -> !name.contains(","))
                    .map(name -> matchesPattern(builder, movie.get("genres"),
                            "(?:\\A|,)" + TOKEN_WHITESPACE + Pattern.quote(name) + TOKEN_WHITESPACE + "(?:,|\\z)"))
                    .toArray(Predicate[]::new)));
        }
        return builder.or(builder.and(linked.toArray(Predicate[]::new)), builder.and(csv.toArray(Predicate[]::new)));
    }

    private static Predicate hasGenreLink(Root<Movie> movie, CriteriaQuery<?> query, CriteriaBuilder builder,
            Set<String> names) {
        Subquery<Integer> linkQuery = query.subquery(Integer.class);
        Root<MovieGenre> link = linkQuery.from(MovieGenre.class);
        Predicate sameMovie = builder.equal(link.get("movieId"), movie.get("id"));
        linkQuery.select(builder.literal(1));
        if (names == null) {
            linkQuery.where(sameMovie);
        } else {
            linkQuery.where(sameMovie, builder.lower(link.get("genreName")).in(names));
        }
        return builder.exists(linkQuery);
    }

    // Truyền cờ "c" sau khi đã lower() để tránh H2/MySQL tự bỏ dấu tiếng Việt làm sai kết quả lọc.
    private static Predicate matchesPattern(CriteriaBuilder builder, Expression<String> column, String pattern) {
        return builder.isTrue(builder.function("regexp_like", Boolean.class, builder.lower(column),
                ((HibernateCriteriaBuilder) builder).value(pattern), builder.literal("c")));
    }

    @Override
    public boolean test(Movie movie) {
        if (movie == null) {
            return false;
        }
        return matches(movie.getTitle(), movie.getDescription(), movie.getGenres());
    }

    // So khớp phim trên bộ nhớ theo đủ mọi thể loại yêu cầu và từ khóa hiện tại.
    public boolean matches(String title, String description, String genres) {
        for (String token : genreTokens) {
            if (!matchesGenreToken(genres, token)) {
                return false;
            }
        }
        return matchesSearch(title, description, searchTerm);
    }

    // Tách theo dấu phẩy rồi so khớp nguyên token (không nhận chuỗi con như "AnimationExtra" cho "animation").
    public static boolean matchesGenreToken(String genresText, String rawToken) {
        String token = normalizeTerm(rawToken);
        if (token == null) {
            return true;
        }
        if (genresText == null || genresText.isEmpty()) {
            return false;
        }
        for (String genreName : genresText.split(",")) {
            if (genreName.strip().toLowerCase(Locale.ROOT).equals(token)) {
                return true;
            }
        }
        return false;
    }

    // Kiểm tra từ khóa có nằm trong tiêu đề hoặc mô tả không (không phân biệt hoa thường, giữ nguyên dấu).
    public static boolean matchesSearch(String title, String description, String rawQuery) {
        String term = normalizeTerm(rawQuery);
        if (term == null) {
            return true;
        }
        boolean titleMatch = title != null && title.toLowerCase(Locale.ROOT).contains(term);
        boolean descriptionMatch = description != null && description.toLowerCase(Locale.ROOT).contains(term);
        return titleMatch || descriptionMatch;
    }

    private static String normalizeTerm(String raw) {
        if (raw == null) {
            return null;
        }
        String stripped = raw.strip();
        return stripped.isEmpty() ? null : stripped.toLowerCase(Locale.ROOT);
    }
}
