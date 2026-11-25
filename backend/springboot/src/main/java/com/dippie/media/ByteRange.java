package com.dippie.media;

import java.math.BigInteger;

public record ByteRange(long start, long end, long totalLength, boolean partial) {

    private static final String BYTES_PREFIX = "bytes=";

    public static ByteRange parse(String header, long totalLength) {
        if (totalLength < 0) {
            throw new IllegalArgumentException("totalLength must be non-negative");
        }
        if (header == null || header.isBlank()) {
            return new ByteRange(0, totalLength - 1, totalLength, false);
        }
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
        return parseBoundedOrOpenRange(spec, totalLength);
    }

    public long length() {
        return partial ? (end - start + 1) : totalLength;
    }

    public String contentRangeHeader() {
        return "bytes " + start + "-" + end + "/" + totalLength;
    }

    private static ByteRange parseSuffixRange(String suffixText, long totalLength) {
        BigInteger suffix = parseNonNegativeInteger(suffixText, totalLength);
        if (suffix.signum() <= 0) {
            throw new RangeNotSatisfiableException(totalLength);
        }
        BigInteger total = BigInteger.valueOf(totalLength);
        long start = suffix.compareTo(total) >= 0 ? 0L : total.subtract(suffix).longValueExact();
        return new ByteRange(start, totalLength - 1, totalLength, true);
    }

    private static ByteRange parseBoundedOrOpenRange(String spec, long totalLength) {
        int dashIndex = spec.indexOf('-');
        String startText = spec.substring(0, dashIndex).trim();
        String endText = spec.substring(dashIndex + 1).trim();

        BigInteger startValue = parseNonNegativeInteger(startText, totalLength);
        BigInteger total = BigInteger.valueOf(totalLength);
        if (startValue.compareTo(total) >= 0) {
            throw new RangeNotSatisfiableException(totalLength);
        }
        long start = startValue.longValueExact();
        long end;
        if (endText.isEmpty()) {
            end = totalLength - 1;
        } else {
            BigInteger endValue = parseNonNegativeInteger(endText, totalLength);
            if (startValue.compareTo(endValue) > 0) {
                throw new RangeNotSatisfiableException(totalLength);
            }
            BigInteger lastByte = BigInteger.valueOf(totalLength - 1);
            end = endValue.min(lastByte).longValueExact();
        }
        return new ByteRange(start, end, totalLength, true);
    }

    private static BigInteger parseNonNegativeInteger(String text, long totalLength) {
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
