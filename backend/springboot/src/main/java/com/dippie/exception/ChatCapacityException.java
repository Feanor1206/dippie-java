package com.dippie.exception;

// ném khi /api/chat chạm trần rate limit hoặc hết slot luồng SSE để controller trả 429 kèm Retry-After
public class ChatCapacityException extends RuntimeException {

    private final long retryAfterSeconds;

    public ChatCapacityException(String message) {
        this(message, 5L);
    }

    public ChatCapacityException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
