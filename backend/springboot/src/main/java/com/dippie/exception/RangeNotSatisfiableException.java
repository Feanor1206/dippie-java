package com.dippie.exception;

// ném khi header HTTP Range vượt kích thước file, mang kèm totalLength để trả header 416 đúng chuẩn
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
