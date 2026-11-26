package com.dippie.dto.admin;

// Thông tin một tập phim trong top xem nhiều trên màn hình thống kê admin.
public record TopWatchedEpisodeView(String episodeId, String title, long viewers) {
}
