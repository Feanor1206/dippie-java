package com.dippie.dto.admin;

import java.util.List;

// Gói toàn bộ chỉ số vận hành trả cho màn hình thống kê, sao chép hai danh sách xếp hạng thành bất biến.
public record AdminStatsResponse(
        long totalUsers,
        long activeUsers,
        long totalMovies,
        long publishedEpisodes,
        long totalWatchSessions,
        long completedWatchSessions,
        double completionRate,
        List<TopWatchedEpisodeView> topWatchedEpisodes,
        List<TopRatedMovieView> topRatedMovies,
        long newUsersLast7Days
) {

    public AdminStatsResponse {
        topWatchedEpisodes = topWatchedEpisodes == null ? List.of() : List.copyOf(topWatchedEpisodes);
        topRatedMovies = topRatedMovies == null ? List.of() : List.copyOf(topRatedMovies);
    }
}
