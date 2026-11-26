package com.dippie.model.catalog;

// thông tin gọn của một tập cho SeriesCard và màn chi tiết, tách khỏi CatalogReadModel để model khỏi kéo ngược vào service
// memberAccessible/adminAccessible phân biệt tập phát thẳng được trong Dippie (YouTube nhúng hoặc file nội bộ) với tập chỉ mở ngoài
public record SeriesEpisode(String id, String title, Integer seasonNumber, Integer episodeNumber, Long durationMs,
        boolean memberAccessible, boolean adminAccessible, String seriesId, String seasonId, SeriesArtwork artwork) {
}
