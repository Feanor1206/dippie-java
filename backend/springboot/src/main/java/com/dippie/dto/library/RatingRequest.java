package com.dippie.dto.library;

// body nhận movieId với điểm lúc user chấm phim
public record RatingRequest(String movieId, Integer rating) {
}
