package com.dippie.model.catalog;

// Thông tin rút gọn của một tập phim dùng cho thẻ bộ phim và màn chi tiết.
// Tách ra khỏi CatalogReadModel để tầng model (SeriesCard) không phải phụ thuộc ngược vào service.
// Hai cờ memberAccessible/adminAccessible phân biệt tập thực sự phát được trong Dippie (YouTube nhúng được hoặc file nội bộ hợp lệ) với tập chỉ mở ngoài.
public record SeriesEpisode(String id, String title, Integer seasonNumber, Integer episodeNumber, Long durationMs,
        boolean memberAccessible, boolean adminAccessible, String seriesId, String seasonId, SeriesArtwork artwork) {
}
