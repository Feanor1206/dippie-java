package com.dippie.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

// Kiểm tra các trường hợp tách header HTTP Range (đầy đủ, mở đuôi, hậu tố, kẹp số quá lớn và lỗi 416) của ByteRange.
class ByteRangeTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void returnsFullRangeWhenHeaderIsBlank(String header) {
        ByteRange range = ByteRange.parse(header, 100);

        assertThat(range.partial()).isFalse();
        assertThat(range.start()).isZero();
        assertThat(range.end()).isEqualTo(99);
        assertThat(range.length()).isEqualTo(100);
        assertThat(range.totalLength()).isEqualTo(100);
    }

    @Test
    void returnsEmptyFullRangeForZeroLengthFileWithoutHeader() {
        ByteRange range = ByteRange.parse(null, 0);

        assertThat(range.partial()).isFalse();
        assertThat(range.length()).isZero();
        assertThat(range.totalLength()).isZero();
    }

    @Test
    void parsesBoundedRange() {
        ByteRange range = ByteRange.parse("bytes=10-29", 100);

        assertThat(range.partial()).isTrue();
        assertThat(range.start()).isEqualTo(10);
        assertThat(range.end()).isEqualTo(29);
        assertThat(range.length()).isEqualTo(20);
        assertThat(range.contentRangeHeader()).isEqualTo("bytes 10-29/100");
    }

    @Test
    void parsesSingleByteRangeAndTrimsWhitespace() {
        ByteRange single = ByteRange.parse("bytes=0-0", 1);
        assertThat(single.start()).isZero();
        assertThat(single.end()).isZero();
        assertThat(single.length()).isEqualTo(1);
        assertThat(single.contentRangeHeader()).isEqualTo("bytes 0-0/1");

        ByteRange spaced = ByteRange.parse("  bytes= 1 - 5  ", 12);
        assertThat(spaced.start()).isEqualTo(1);
        assertThat(spaced.end()).isEqualTo(5);
        assertThat(spaced.length()).isEqualTo(5);
        assertThat(spaced.contentRangeHeader()).isEqualTo("bytes 1-5/12");
    }

    @Test
    void parsesOpenEndedRange() {
        ByteRange range = ByteRange.parse("bytes=10-", 100);

        assertThat(range.partial()).isTrue();
        assertThat(range.start()).isEqualTo(10);
        assertThat(range.end()).isEqualTo(99);
        assertThat(range.length()).isEqualTo(90);
        assertThat(range.contentRangeHeader()).isEqualTo("bytes 10-99/100");
    }

    @Test
    void parsesSuffixRangeAndClampsToStart() {
        ByteRange tail = ByteRange.parse("bytes=-5", 100);
        assertThat(tail.partial()).isTrue();
        assertThat(tail.start()).isEqualTo(95);
        assertThat(tail.end()).isEqualTo(99);
        assertThat(tail.length()).isEqualTo(5);
        assertThat(tail.contentRangeHeader()).isEqualTo("bytes 95-99/100");

        ByteRange largerThanFile = ByteRange.parse("bytes=-500", 100);
        assertThat(largerThanFile.start()).isZero();
        assertThat(largerThanFile.end()).isEqualTo(99);
        assertThat(largerThanFile.length()).isEqualTo(100);

        ByteRange hugeSuffix = ByteRange.parse("bytes=-999999999999999999999999999", 100);
        assertThat(hugeSuffix.start()).isZero();
        assertThat(hugeSuffix.end()).isEqualTo(99);
        assertThat(hugeSuffix.length()).isEqualTo(100);
    }

    @Test
    void clampsEndToLastByte() {
        ByteRange clamped = ByteRange.parse("bytes=90-500", 100);
        assertThat(clamped.partial()).isTrue();
        assertThat(clamped.start()).isEqualTo(90);
        assertThat(clamped.end()).isEqualTo(99);
        assertThat(clamped.length()).isEqualTo(10);
        assertThat(clamped.contentRangeHeader()).isEqualTo("bytes 90-99/100");

        ByteRange hugeEnd = ByteRange.parse("bytes=0-999999999999999999999999999", 100);
        assertThat(hugeEnd.start()).isZero();
        assertThat(hugeEnd.end()).isEqualTo(99);
        assertThat(hugeEnd.length()).isEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "bytes=0-0",
            "bytes=0-",
            "bytes=-1"
    })
    void rejectsAnyRangeOnEmptyFile(String header) {
        assertThatThrownBy(() -> ByteRange.parse(header, 0))
                .isInstanceOf(RangeNotSatisfiableException.class)
                .satisfies(ex -> {
                    RangeNotSatisfiableException rangeEx = (RangeNotSatisfiableException) ex;
                    assertThat(rangeEx.totalLength()).isZero();
                    assertThat(rangeEx.contentRangeHeader()).isEqualTo("bytes */0");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "items=0-5",
            "BYTES=0-5",
            "0-5",
            "bytes=",
            "bytes=10",
            "bytes=-",
            "bytes=-0",
            "bytes=--5",
            "bytes=-+5",
            "bytes=- 5",
            "bytes=-abc",
            "bytes=100-",
            "bytes=101-120",
            "bytes=999999999999999999999999999-",
            "bytes=10-5",
            "bytes=1-0",
            "bytes=0-1,2-3",
            "bytes=a-b",
            "bytes=0-b",
            "bytes=+0-5",
            "bytes=0-+5",
            "bytes=0--5",
            "bytes=0-1-2"
    })
    void rejectsMalformedAndUnsatisfiableRanges(String header) {
        assertThatThrownBy(() -> ByteRange.parse(header, 100))
                .isInstanceOf(RangeNotSatisfiableException.class)
                .satisfies(ex -> {
                    RangeNotSatisfiableException rangeEx = (RangeNotSatisfiableException) ex;
                    assertThat(rangeEx.totalLength()).isEqualTo(100);
                    assertThat(rangeEx.contentRangeHeader()).isEqualTo("bytes */100");
                });
    }

    @Test
    void rejectsNegativeTotalLength() {
        assertThatThrownBy(() -> ByteRange.parse(null, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
