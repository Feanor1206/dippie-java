package com.dippie.library;

// Body nhận movieId và số điểm khi người dùng chấm phim.
public record RatingRequest(String movieId, Integer rating) {
}
