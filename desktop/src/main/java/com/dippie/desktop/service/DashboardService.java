package com.dippie.desktop.service;

import com.dippie.desktop.dao.EpisodeDao;
import com.dippie.desktop.dao.RatingDao;
import com.dippie.desktop.dao.RatingDao.TopRated;
import com.dippie.desktop.dao.SeriesDao;
import com.dippie.desktop.dao.UserDao;
import com.dippie.desktop.dao.WatchProgressDao;
import com.dippie.desktop.dao.WatchProgressDao.MostWatched;
import com.dippie.desktop.db.JdbcDatabase;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Service cho tab Tổng quan, gom số liệu, bảng xếp hạng và phân bố điểm trong một lượt đọc DB.
public final class DashboardService {
    private final JdbcDatabase db;
    private final SeriesDao seriesDao;
    private final EpisodeDao episodeDao;
    private final UserDao userDao;
    private final RatingDao ratingDao;
    private final WatchProgressDao watchProgressDao;

    public DashboardService(JdbcDatabase db, SeriesDao seriesDao, EpisodeDao episodeDao,
                            UserDao userDao, RatingDao ratingDao, WatchProgressDao watchProgressDao) {
        this.db = db;
        this.seriesDao = seriesDao;
        this.episodeDao = episodeDao;
        this.userDao = userDao;
        this.ratingDao = ratingDao;
        this.watchProgressDao = watchProgressDao;
    }

    // Điền sẵn đủ 10 mức điểm từ 1 đến 10 bằng 0 trước khi đổ số thật vào để biểu đồ cột không bị khuyết cột.
    public Snapshot load() {
        return db.read(connection -> {
            var counts = new Counts(seriesDao.count(connection), episodeDao.count(connection), userDao.count(connection),
                    ratingDao.count(connection), watchProgressDao.count(connection));
            Map<Integer, Long> distribution = new LinkedHashMap<>();
            for (int score = 1; score <= 10; score++) {
                distribution.put(score, 0L);
            }
            for (var scoreCount : ratingDao.scoreCounts(connection)) {
                distribution.put(scoreCount.score(), scoreCount.count());
            }
            return new Snapshot(counts, ratingDao.topRatedEpisodes(connection), watchProgressDao.mostWatchedSeries(connection),
                    Map.copyOf(distribution));
        });
    }

    // Số lượng tổng của 5 thẻ thống kê trên hàng đầu tab Tổng quan.
    public record Counts(long series, long episodes, long users, long ratings, long progress) {
    }

    // Toàn bộ dữ liệu của một lần tải Tổng quan để cập nhật bảng và biểu đồ cùng lúc.
    public record Snapshot(Counts counts, List<TopRated> topRated, List<MostWatched> mostWatched,
                           Map<Integer, Long> ratingCounts) {
    }
}
