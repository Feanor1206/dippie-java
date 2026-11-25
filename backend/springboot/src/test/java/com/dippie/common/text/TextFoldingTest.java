package com.dippie.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// Test đơn vị cho TextFolding: bỏ dấu tiếng Việt, hạ chữ thường và gộp khoảng trắng khớp với bản Python.
class TextFoldingTest {

    static Stream<Arguments> parityCases() {
        return Stream.of(
                Arguments.of("Xin chào Jadoo!", "xin chao jadoo!"),
                Arguments.of("ĐÊM TRĂNG MÁU — tập 01", "dem trang mau — tap 01"),
                Arguments.of("Phim hài hước, vui nhộn & ấm áp", "phim hai huoc, vui nhon & am ap"),
                Arguments.of("   Nhiều   khoảng   trắng   ", "   nhieu   khoang   trang   "),
                Arguments.of("đĐ ăâêôơư ĂÂÊÔƠƯ", "dd aaeoou aaeoou"),
                Arguments.of("Tập phim của bộ hoạt hình", "tap phim cua bo hoat hinh")
        );
    }

    @ParameterizedTest
    @MethodSource("parityCases")
    void matchesPythonFoldText(String input, String expected) {
        assertThat(TextFolding.fold(input)).isEqualTo(expected);
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(TextFolding.fold(null)).isEmpty();
        assertThat(TextFolding.fold("")).isEmpty();
        assertThat(TextFolding.collapseWhitespace("  a\t\nb  ")).isEqualTo("a b");
        assertThat(TextFolding.foldNormalized("  Xin   Chào  ")).isEqualTo("xin chao");
    }
}
