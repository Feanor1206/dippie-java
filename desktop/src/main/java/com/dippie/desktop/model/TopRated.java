package com.dippie.desktop.model;

// Một dòng trong bảng 10 tập điểm cao nhất. Mình tách từ RatingDao sang model để DashboardPane khỏi đụng thẳng tầng DAO nha Việt.
public record TopRated(String episodeId, String seriesTitle, String title, double average, long count) {
}
