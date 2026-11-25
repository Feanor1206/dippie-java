package com.dippie.movie;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Test đơn vị cho MovieFilter: khớp nguyên token thể loại (không ăn chuỗi con hay lệch dấu) và tìm không phân biệt hoa thường.
class MovieFilterTest {

    @Test
    void matchesExactCaseInsensitiveGenreTokensWithoutSubstringOrDiacriticFolding() {
        assertThat(MovieFilter.matchesGenreToken(" animation , Family ", "Animation")).isTrue();
        assertThat(MovieFilter.matchesGenreToken("Animation, Family", "family")).isTrue();
        assertThat(MovieFilter.matchesGenreToken("Animàtion, Family", "animation")).isFalse();
        assertThat(MovieFilter.matchesGenreToken("AnimationExtra, Family", "animation")).isFalse();
        assertThat(MovieFilter.matchesGenreToken(null, "animation")).isFalse();
        assertThat(MovieFilter.matchesGenreToken(null, "   ")).isTrue();
    }

    @Test
    void matchesSearchInTitleOrDescriptionWithUnicodeCaseInsensitivity() {
        Movie beach = new Movie(
                "beach",
                "Kỳ nghỉ",
                2024,
                8.5,
                "90 min",
                "Animation, Family",
                "Một tuần ở BỜ BIỂN",
                null,
                null,
                null
        );

        assertThat(new MovieFilter("bờ biển", List.of("animation", "family")).test(beach)).isTrue();
        assertThat(new MovieFilter("bo bien", List.of("animation")).test(beach)).isFalse();
        assertThat(new MovieFilter("KỲ NGHỈ", List.of()).test(beach)).isTrue();
        assertThat(new MovieFilter("bờ biển", List.of("animation", "comedy")).test(beach)).isFalse();
        assertThat(MovieFilter.forSingleGenre(null, null).test(beach)).isTrue();
        assertThat(MovieFilter.forSingleGenre(null, null).test(null)).isFalse();
    }
}
