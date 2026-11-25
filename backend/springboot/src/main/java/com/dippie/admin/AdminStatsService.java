package com.dippie.admin;

import com.dippie.security.CurrentViewer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStatsService {

    private static final int TOP_LIMIT = 10;
    private static final int NEW_USER_DAYS = 7;
    private static final int RATE_SCALE = 4;

    private final CurrentViewer currentViewer;
    private final AdminStatsQuery adminStatsQuery;
    private final Clock clock;

    public AdminStatsService(CurrentViewer currentViewer, AdminStatsQuery adminStatsQuery, Clock clock) {
        this.currentViewer = currentViewer;
        this.adminStatsQuery = adminStatsQuery;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse collectStats() {
        currentViewer.requireAdmin();
        long totalUsers = adminStatsQuery.countTotalUsers();
        long activeUsers = adminStatsQuery.countActiveUsers();
        long totalMovies = adminStatsQuery.countTotalMovies();
        long publishedEpisodes = adminStatsQuery.countPublishedEpisodes();
        long totalWatchSessions = adminStatsQuery.countTotalWatchSessions();
        long completedWatchSessions = adminStatsQuery.countCompletedWatchSessions();
        double completionRate = computeCompletionRate(completedWatchSessions, totalWatchSessions);
        List<TopWatchedEpisodeView> topWatchedEpisodes = adminStatsQuery.findTopWatchedEpisodes(TOP_LIMIT);
        List<TopRatedMovieView> topRatedMovies = adminStatsQuery.findTopRatedMovies(TOP_LIMIT);
        long newUsersLast7Days = adminStatsQuery.countNewUsersSince(LocalDateTime.now(clock).minusDays(NEW_USER_DAYS));

        return new AdminStatsResponse(
                totalUsers,
                activeUsers,
                totalMovies,
                publishedEpisodes,
                totalWatchSessions,
                completedWatchSessions,
                completionRate,
                topWatchedEpisodes,
                topRatedMovies,
                newUsersLast7Days
        );
    }

    private static double computeCompletionRate(long completed, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(completed)
                .divide(BigDecimal.valueOf(total), RATE_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
