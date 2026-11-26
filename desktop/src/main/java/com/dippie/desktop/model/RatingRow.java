package com.dippie.desktop.model;

// Một dòng đầy đủ (đánh giá kèm tên người dùng và tên phim) để hiển thị và lọc trên bảng đánh giá.
// Tách khỏi RatingService sang model để RatingsPane không cần nhập lớp từ tầng service nữa nha Việt.
public record RatingRow(Rating rating, String username, String title, String seriesId, String seriesTitle) {
    public long id() {
        return rating.id();
    }
}
