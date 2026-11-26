package com.dippie.desktop.model;

// 1 dòng đầy đủ (đánh giá kèm username và tên phim) để hiện và lọc trên bảng đánh giá
// tách khỏi RatingService qua model để RatingsPane khỏi nhập lớp từ service nữa nha Việt
public record RatingRow(Rating rating, String username, String title, String seriesId, String seriesTitle) {
    public long id() {
        return rating.id();
    }
}
