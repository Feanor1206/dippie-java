package com.dippie.admin;

import java.util.List;

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
}
