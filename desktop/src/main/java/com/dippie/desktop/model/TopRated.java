package com.dippie.desktop.model;

// 1 dòng bảng 10 tập điểm cao nhất, tách từ RatingDao sang model để DashboardPane khỏi đụng thẳng DAO nha Việt
public record TopRated(String episodeId, String seriesTitle, String title, double average, long count) {
}
