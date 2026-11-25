package com.dippie.admin;

import com.dippie.security.CurrentViewer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Tổng hợp các chỉ số thống kê trong một giao dịch chỉ đọc và tính tỷ lệ hoàn thành phiên xem.
@Service
class AdminStatsService {

    private static final int TOP_LIMIT = 10;
    private static final int NEW_USER_DAYS = 7;
    private static final int RATE_SCALE = 4;

    private final CurrentViewer currentViewer;
    private final AdminStatsQuery adminStatsQuery;
    private final Clock clock;

    AdminStatsService(CurrentViewer currentViewer, AdminStatsQuery adminStatsQuery, Clock clock) {
        this.currentViewer = currentViewer;
        this.adminStatsQuery = adminStatsQuery;
        this.clock = clock;
    }

    // Kiểm tra quyền admin rồi đọc đồng bộ số liệu người dùng, phim, lượt xem và số đăng ký mới 7 ngày qua.
    @Transactional(readOnly = true)
    public AdminStatsResponse collectStats() {
        currentViewer.requireAdmin();
        AdminStatsQuery.UserCounts users = adminStatsQuery.countUsers(LocalDateTime.now(clock).minusDays(NEW_USER_DAYS));
        long totalMovies = adminStatsQuery.countTotalMovies();
        long publishedEpisodes = adminStatsQuery.countPublishedEpisodes();
        AdminStatsQuery.WatchSessionCounts watchSessions = adminStatsQuery.countWatchSessions();
        double completionRate = computeCompletionRate(watchSessions.completed(), watchSessions.total());
        List<TopWatchedEpisodeView> topWatchedEpisodes = adminStatsQuery.findTopWatchedEpisodes(TOP_LIMIT);
        List<TopRatedMovieView> topRatedMovies = adminStatsQuery.findTopRatedMovies(TOP_LIMIT);

        return new AdminStatsResponse(users.total(), users.active(), totalMovies, publishedEpisodes,
                watchSessions.total(), watchSessions.completed(), completionRate, topWatchedEpisodes,
                topRatedMovies, users.recent());
    }

    // Trả 0.0 khi chưa có phiên xem nào để tránh chia cho 0, còn lại làm tròn 4 chữ số thập phân.
    private static double computeCompletionRate(long completed, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(completed)
                .divide(BigDecimal.valueOf(total), RATE_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
