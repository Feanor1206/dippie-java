package com.dippie.dto.admin;

// một dòng bảng top phim điểm cao kèm điểm trung bình và số lượt chấm
public record TopRatedMovieView(String movieId, String title, double averageRating, long ratingCount) {
}
