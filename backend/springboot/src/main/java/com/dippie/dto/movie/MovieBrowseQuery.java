package com.dippie.dto.movie;

import com.dippie.exception.InvalidRequestException;
import com.dippie.model.movie.MovieFilter;

import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Chuẩn hóa và kiểm tra bộ tham số duyệt phim (tìm kiếm, thể loại, năm, sắp xếp, phân trang).
public record MovieBrowseQuery(
        String searchQuery, List<String> genreTokens, Integer yearMin, Integer yearMax, SortField sortField,
        SortDirection sortDirection, boolean explicitSort, BigInteger page, int size
) {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String ANIMATION_GENRE = "animation";

    public MovieBrowseQuery {
        genreTokens = genreTokens == null ? List.of() : List.copyOf(genreTokens);
    }

    // Các tiêu chí sắp xếp được hỗ trợ trên màn hình duyệt phim.
    public enum SortField {
        RATING, YEAR, TITLE;

        // Chuỗi rỗng hoặc null mặc định về RATING, còn chuỗi chỉ có khoảng trắng thì bị coi là sai để báo lỗi rõ.
        public static SortField parse(String raw) {
            String normalized = (raw == null || raw.isEmpty() ? "rating" : raw).strip().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "rating" -> RATING;
                case "year" -> YEAR;
                case "title" -> TITLE;
                default -> throw new InvalidRequestException("Kiểu sắp xếp không hỗ trợ: '" + raw + "'.");
            };
        }
    }

    // Thứ tự sắp xếp tăng dần hoặc giảm dần.
    public enum SortDirection {
        ASC, DESC;

        // Mặc định giảm dần khi client bỏ trống tham số direction.
        public static SortDirection parse(String raw) {
            String normalized = (raw == null || raw.isEmpty() ? "desc" : raw).strip().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "asc" -> ASC;
                case "desc" -> DESC;
                default -> throw new InvalidRequestException("Thứ tự sắp xếp không hợp lệ: '" + raw + "'.");
            };
        }
    }

    // Gom 3 alias query/search/q, chuẩn hóa danh sách thể loại rồi chặn sớm các tham số phân trang hoặc khoảng năm vô lý.
    public static MovieBrowseQuery of(String query, String search, String q, String genre, String genres,
            Boolean animationOnly, Integer yearMin, Integer yearMax, String sort, String direction,
            BigInteger page, int size) {
        BigInteger resolvedPage = page == null ? BigInteger.ZERO : page;
        if (resolvedPage.signum() < 0) {
            throw new InvalidRequestException("Số trang không được nhỏ hơn 0: " + resolvedPage);
        }
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("Số phim mỗi trang phải từ 1 đến 100: " + size);
        }
        if (yearMin != null && yearMax != null && yearMin > yearMax) {
            throw new InvalidRequestException(
                    "Năm bắt đầu (" + yearMin + ") không được lớn hơn năm kết thúc (" + yearMax + ").");
        }
        return new MovieBrowseQuery(firstNonBlank(query, search, q), collectGenreTokens(genre, genres, animationOnly),
                yearMin, yearMax, SortField.parse(sort), SortDirection.parse(direction),
                sort != null && !sort.isEmpty(), resolvedPage, size);
    }

    public static MovieBrowseQuery of(String query, String search, String q, String genre, String genres,
            Boolean animationOnly, Integer yearMin, Integer yearMax, String sort, String direction,
            int page, int size) {
        return of(query, search, q, genre, genres, animationOnly, yearMin, yearMax, sort, direction,
                BigInteger.valueOf(page), size);
    }

    public MovieFilter genreFilter() {
        return new MovieFilter(null, genreTokens);
    }

    // Lấy chuỗi đầu tiên không rỗng theo thứ tự ưu tiên tham số truyền vào.
    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }

    private static List<String> collectGenreTokens(String genre, String genres, Boolean animationOnly) {
        Set<String> tokens = new LinkedHashSet<>();
        addToken(tokens, genre);
        if (genres != null && !genres.isEmpty()) {
            for (String part : genres.split(",")) {
                addToken(tokens, part);
            }
        }
        if (Boolean.TRUE.equals(animationOnly)) {
            tokens.add(ANIMATION_GENRE);
        }
        return List.copyOf(tokens);
    }

    private static void addToken(Set<String> tokens, String raw) {
        if (raw == null) {
            return;
        }
        String stripped = raw.strip();
        if (!stripped.isEmpty()) {
            tokens.add(stripped.toLowerCase(Locale.ROOT));
        }
    }
}
