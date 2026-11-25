package com.dippie.common.web;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public record ApiResponse<T>(boolean success, String message, T data, LocalDateTime timestamp) {

    public static final String DEFAULT_SUCCESS_MESSAGE = "Thành công";

    public static <T> ApiResponse<T> ok(T data) {
        return ok(data, DEFAULT_SUCCESS_MESSAGE);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, message, data, now());
    }

    public static <T> ApiResponse<T> failure(String message) {
        return new ApiResponse<>(false, message, null, now());
    }

    public static <T> ApiResponse<T> failure(String message, T data) {
        return new ApiResponse<>(false, message, data, now());
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
