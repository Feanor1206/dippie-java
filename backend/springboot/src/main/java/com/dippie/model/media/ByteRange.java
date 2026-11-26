package com.dippie.model.media;

import com.dippie.exception.RangeNotSatisfiableException;

import java.math.BigInteger;

// khoảng byte cho request stream nguyên file (200) hay từng khúc (206)
public record ByteRange(long start, long end, long totalLength, boolean partial) {

    private static final String BYTES_PREFIX = "bytes=";

    // tách header Range chuẩn HTTP, sai cú pháp hay lố cỡ file thì ném 416
    public static ByteRange parse(String header, long totalLength) {
        if (totalLength < 0) {
            throw new IllegalArgumentException("totalLength must be non-negative");
        }
        if (header == null || header.isBlank()) {
            return new ByteRange(0, totalLength - 1, totalLength, false);
        }
        // file rỗng hay header không mở đầu bằng bytes= thì đá 416 luôn
        String trimmed = header.trim();
        if (totalLength == 0 || !trimmed.startsWith(BYTES_PREFIX)) {
            throw new RangeNotSatisfiableException(totalLength);
        }
        String afterPrefix = trimmed.substring(BYTES_PREFIX.length());
        if (!afterPrefix.contains("-")) {
            throw new RangeNotSatisfiableException(totalLength);
        }
        String spec = afterPrefix.trim();
        if (spec.startsWith("-")) {
            return parseSuffixRange(spec.substring(1), totalLength);
        }
        return parseRange(spec, totalLength);
    }

    public long length() {
        return partial ? (end - start + 1) : totalLength;
    }

    public String contentRangeHeader() {
        return "bytes " + start + "-" + end + "/" + totalLength;
    }

    // kiểu bytes=-N là lấy N byte cuối, N to hơn cỡ file thì kéo mốc đầu về 0
    private static ByteRange parseSuffixRange(String suffixText, long totalLength) {
        BigInteger suffix = parseDigits(suffixText, totalLength);
        if (suffix.signum() <= 0) {
            throw new RangeNotSatisfiableException(totalLength);
        }
        BigInteger total = BigInteger.valueOf(totalLength);
        long start = suffix.compareTo(total) >= 0 ? 0L : total.subtract(suffix).longValueExact();
        return new ByteRange(start, totalLength - 1, totalLength, true);
    }

    // xử lý bytes=A-B hay bytes=A-, B lố cuối file thì kẹp ở byte cuối chứ không báo lỗi
    private static ByteRange parseRange(String spec, long totalLength) {
        int dashIndex = spec.indexOf('-');
        String startText = spec.substring(0, dashIndex).trim();
        String endText = spec.substring(dashIndex + 1).trim();

        BigInteger startValue = parseDigits(startText, totalLength);
        BigInteger total = BigInteger.valueOf(totalLength);
        if (startValue.compareTo(total) >= 0) {
            throw new RangeNotSatisfiableException(totalLength);
        }
        long start = startValue.longValueExact();
        long end;
        if (endText.isEmpty()) {
            end = totalLength - 1;
        } else {
            BigInteger endValue = parseDigits(endText, totalLength);
            if (startValue.compareTo(endValue) > 0) {
                throw new RangeNotSatisfiableException(totalLength);
            }
            BigInteger lastByte = BigInteger.valueOf(totalLength - 1);
            end = endValue.min(lastByte).longValueExact();
        }
        return new ByteRange(start, end, totalLength, true);
    }

    // dùng BigInteger để client gửi số siêu to vẫn kẹp hoặc trả 416 gọn, không tràn long
    private static BigInteger parseDigits(String text, long totalLength) {
        if (text.isEmpty()) {
            throw new RangeNotSatisfiableException(totalLength);
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch < '0' || ch > '9') {
                throw new RangeNotSatisfiableException(totalLength);
            }
        }
        return new BigInteger(text);
    }
}
