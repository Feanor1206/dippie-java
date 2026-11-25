package com.dippie.movie;

import com.dippie.common.error.InvalidRequestException;

import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

record MovieBrowseQuery(
        String searchQuery,
        List<String> genreTokens,
        Integer yearMin,
        Integer yearMax,
        SortField sortField,
        SortDirection sortDirection,
        BigInteger page,
        int size
) {

    MovieBrowseQuery {
        genreTokens = genreTokens == null ? List.of() : List.copyOf(genreTokens);
    }

    public enum SortField {
        RATING,
        YEAR,
        TITLE;

        public static SortField parse(String raw) {
            String source = (raw == null || raw.isEmpty()) ? "rating" : raw;
            String normalized = source.strip().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "rating" -> RATING;
                case "year" -> YEAR;
                case "title" -> TITLE;
                default -> throw new InvalidRequestException("Trường sắp xếp không hợp lệ: '" + raw + "'");
            };
        }
    }

    public enum SortDirection {
        ASC,
        DESC;

        public static SortDirection parse(String raw) {
            String source = (raw == null || raw.isEmpty()) ? "desc" : raw;
            String normalized = source.strip().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "asc" -> ASC;
                case "desc" -> DESC;
                default -> throw new InvalidRequestException("Chiều sắp xếp không hợp lệ: '" + raw + "'");
            };
        }
    }

    public static MovieBrowseQuery of(
            String query,
            String search,
            String q,
            String genre,
            String genres,
            Boolean animationOnly,
            Integer yearMin,
            Integer yearMax,
            String sort,
            String direction,
            BigInteger page,
            int size
    ) {
        BigInteger resolvedPage = page == null ? BigInteger.ZERO : page;
        if (resolvedPage.signum() < 0) {
            throw new InvalidRequestException("Chỉ số trang (page) không được âm: " + resolvedPage);
        }
        if (size < 1 || size > 100) {
            throw new InvalidRequestException("Kích thước trang (size) phải nằm trong khoảng 1 đến 100: " + size);
        }
        if (yearMin != null && yearMax != null && yearMin > yearMax) {
            throw new InvalidRequestException(
                    "Năm tối thiểu (yearMin: " + yearMin + ") không được lớn hơn năm tối đa (yearMax: " + yearMax + ")"
            );
        }
        SortField resolvedSort = SortField.parse(sort);
        SortDirection resolvedDirection = SortDirection.parse(direction);
        String effectiveQuery = firstNonBlank(query, search, q);
        List<String> resolvedGenres = collectGenreTokens(genre, genres, animationOnly);
        return new MovieBrowseQuery(
                effectiveQuery,
                resolvedGenres,
                yearMin,
                yearMax,
                resolvedSort,
                resolvedDirection,
                resolvedPage,
                size
        );
    }

    public static MovieBrowseQuery of(
            String query,
            String search,
            String q,
            String genre,
            String genres,
            Boolean animationOnly,
            Integer yearMin,
            Integer yearMax,
            String sort,
            String direction,
            int page,
            int size
    ) {
        return of(
                query,
                search,
                q,
                genre,
                genres,
                animationOnly,
                yearMin,
                yearMax,
                sort,
                direction,
                BigInteger.valueOf(page),
                size
        );
    }

    public MovieFilter toFilter() {
        return new MovieFilter(searchQuery, genreTokens);
    }

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
            tokens.add("animation");
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
