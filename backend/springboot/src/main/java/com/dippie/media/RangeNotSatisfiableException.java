package com.dippie.media;

public class RangeNotSatisfiableException extends RuntimeException {

    private final long totalLength;

    public RangeNotSatisfiableException(long totalLength) {
        super("Range not satisfiable for total length " + totalLength);
        this.totalLength = totalLength;
    }

    public long totalLength() {
        return totalLength;
    }

    public String contentRangeHeader() {
        return "bytes */" + totalLength;
    }
}
