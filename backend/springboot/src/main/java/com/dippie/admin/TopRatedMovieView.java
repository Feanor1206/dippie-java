package com.dippie.admin;

// Một dòng trong bảng phim được đánh giá cao nhất gồm điểm trung bình và tổng lượt chấm.
public record TopRatedMovieView(String movieId, String title, double averageRating, long ratingCount) {
}
