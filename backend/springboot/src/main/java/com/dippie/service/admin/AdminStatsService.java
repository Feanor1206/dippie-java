package com.dippie.service.admin;

import com.dippie.dto.admin.AdminStatsResponse;
import com.dippie.dto.admin.TopRatedMovieView;
import com.dippie.dto.admin.TopWatchedEpisodeView;
import com.dippie.repository.admin.AdminStatsQuery;
import com.dippie.security.CurrentViewer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

// gom số liệu thống kê trong 1 transaction chỉ đọc rồi tính tỷ lệ xem hết
@Service
public class AdminStatsService {

    private static final int TOP_LIMIT = 10;

    private final CurrentViewer currentViewer;
    private final AdminStatsQuery adminStatsQuery;
    private final Clock clock;

    public AdminStatsService(CurrentViewer currentViewer, AdminStatsQuery adminStatsQuery, Clock clock) {
        this.currentViewer = currentViewer;
        this.adminStatsQuery = adminStatsQuery;
        this.clock = clock;
    }

    // check quyền admin rồi kéo 1 lượt số user, phim, lượt xem và đăng ký mới 7 ngày qua
    @Transactional(readOnly = true)
    public AdminStatsResponse collectStats() {
        currentViewer.requireAdmin();
        AdminStatsQuery.UserCounts users = adminStatsQuery.countUsers(LocalDateTime.now(clock).minusDays(7));
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

    // chưa có phiên xem nào thì trả 0.0 kẻo chia 0, còn lại làm tròn 4 số lẻ
    private static double computeCompletionRate(long completed, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(completed)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
