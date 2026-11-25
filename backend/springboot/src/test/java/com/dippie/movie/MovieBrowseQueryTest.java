package com.dippie.movie;

import com.dippie.common.error.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovieBrowseQueryTest {

    @Test
    void normalizesDefaultsSearchPrecedenceAndGenreTokens() {
        MovieBrowseQuery query = MovieBrowseQuery.of(
                "   ",
                "  Orbit  ",
                "ignored",
                " Sci-Fi ",
                "Family, sci-fi , , Adventure",
                true,
                2010,
                2025,
                " TITLE ",
                " ASC ",
                0,
                12
        );

        assertThat(query.searchQuery()).isEqualTo("Orbit");
        assertThat(query.genreTokens()).containsExactly("sci-fi", "family", "adventure", "animation");
        assertThat(query.yearMin()).isEqualTo(2010);
        assertThat(query.yearMax()).isEqualTo(2025);
        assertThat(query.sortField()).isEqualTo(MovieBrowseQuery.SortField.TITLE);
        assertThat(query.sortDirection()).isEqualTo(MovieBrowseQuery.SortDirection.ASC);
        assertThat(query.page()).isEqualTo(BigInteger.ZERO);
        assertThat(query.size()).isEqualTo(12);
    }

    @Test
    void acceptsArbitrarilyLargePageNumbersAndEmptySortDefaults() {
        BigInteger hugePage = new BigInteger("999999999999999999999");
        MovieBrowseQuery query = MovieBrowseQuery.of(
                null,
                null,
                "bờ biển",
                null,
                null,
                false,
                null,
                null,
                "",
                "",
                hugePage,
                100
        );

        assertThat(query.searchQuery()).isEqualTo("bờ biển");
        assertThat(query.genreTokens()).isEmpty();
        assertThat(query.sortField()).isEqualTo(MovieBrowseQuery.SortField.RATING);
        assertThat(query.sortDirection()).isEqualTo(MovieBrowseQuery.SortDirection.DESC);
        assertThat(query.page()).isEqualTo(hugePage);
        assertThat(query.size()).isEqualTo(100);
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> MovieBrowseQuery.of(
                null, null, null, null, null, null, null, null, "rating", "desc", -1, 12
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Chỉ số trang (page) không được âm: -1");
    }

    @Test
    void rejectsOutOfBoundsPageSize() {
        assertThatThrownBy(() -> MovieBrowseQuery.of(
                null, null, null, null, null, null, null, null, "rating", "desc", 0, 0
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Kích thước trang (size) phải nằm trong khoảng 1 đến 100: 0");

        assertThatThrownBy(() -> MovieBrowseQuery.of(
                null, null, null, null, null, null, null, null, "rating", "desc", 0, 101
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Kích thước trang (size) phải nằm trong khoảng 1 đến 100: 101");
    }

    @Test
    void rejectsInvertedYearRange() {
        assertThatThrownBy(() -> MovieBrowseQuery.of(
                null, null, null, null, null, null, 2025, 2020, "rating", "desc", 0, 12
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Năm tối thiểu (yearMin: 2025) không được lớn hơn năm tối đa (yearMax: 2020)");
    }

    @Test
    void rejectsInvalidSortFieldAndDirection() {
        assertThatThrownBy(() -> MovieBrowseQuery.of(
                null, null, null, null, null, null, null, null, "popularity", "desc", 0, 12
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Trường sắp xếp không hợp lệ: 'popularity'");

        assertThatThrownBy(() -> MovieBrowseQuery.of(
                null, null, null, null, null, null, null, null, "   ", "desc", 0, 12
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Trường sắp xếp không hợp lệ: '   '");

        assertThatThrownBy(() -> MovieBrowseQuery.of(
                null, null, null, null, null, null, null, null, "year", "up", 0, 12
        ))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Chiều sắp xếp không hợp lệ: 'up'");
    }
}
