package com.dippie.dto.admin;

import java.util.List;

// gom mấy số liệu thống kê cho màn admin, copy hai list top sang bất biến luôn
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
