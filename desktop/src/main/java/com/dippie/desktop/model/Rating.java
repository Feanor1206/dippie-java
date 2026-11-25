package com.dippie.desktop.model;

import java.time.LocalDateTime;

// Một dòng đánh giá đọc trực tiếp từ bảng movie_ratings.
public record Rating(long id, long userId, String movieId, int score, LocalDateTime updatedAt) {
}
