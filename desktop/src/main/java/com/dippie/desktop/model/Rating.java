package com.dippie.desktop.model;

import java.time.LocalDateTime;

// 1 dòng đánh giá đọc thẳng từ bảng movie_ratings
public record Rating(long id, long userId, String movieId, int score, LocalDateTime updatedAt) {
}
