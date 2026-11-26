package com.dippie.exception;

// Ném ra khi /api/chat chạm trần rate limit hoặc hết chỗ luồng SSE, controller bắt để trả HTTP 429 kèm Retry-After.
public class ChatCapacityException extends RuntimeException {

    private static final long DEFAULT_RETRY_AFTER_SECONDS = 5L;

    private final long retryAfterSeconds;

    public ChatCapacityException(String message) {
        this(message, DEFAULT_RETRY_AFTER_SECONDS);
    }

    public ChatCapacityException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
