package com.dippie.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

// khóa phát video từ tính năng trẻ em (thú ảo nghỉ mắt hoặc chưa làm ôn tập), trả 423 kèm header lý do
public class PlayFeatureLockedException extends DomainException {

    public PlayFeatureLockedException(String reason, String message) {
        this(reason, message, null);
    }

    public PlayFeatureLockedException(String reason, String message, Long retryAfterSeconds) {
        super(HttpStatus.LOCKED, message, buildHeaders(reason, retryAfterSeconds));
    }

    private static Map<String, String> buildHeaders(String reason, Long retryAfterSeconds) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (reason != null && !reason.isBlank()) {
            headers.put("X-Dippie-Lock-Reason", reason);
        }
        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            headers.put(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        }
        return headers;
    }
}
