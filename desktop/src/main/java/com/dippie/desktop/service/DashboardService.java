package com.dippie.desktop.service;

import com.dippie.desktop.dao.EpisodeDao;
import com.dippie.desktop.dao.RatingDao;
import com.dippie.desktop.dao.SeriesDao;
import com.dippie.desktop.dao.UserDao;
import com.dippie.desktop.dao.WatchProgressDao;
import com.dippie.desktop.db.JdbcDatabase;
import com.dippie.desktop.model.DashboardCounts;
import com.dippie.desktop.model.DashboardSnapshot;

import java.util.LinkedHashMap;
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
    public DashboardSnapshot load() {
        return db.read(connection -> {
            var counts = new DashboardCounts(seriesDao.count(connection), episodeDao.count(connection), userDao.count(connection),
                    ratingDao.count(connection), watchProgressDao.count(connection));
            Map<Integer, Long> distribution = new LinkedHashMap<>();
            for (int score = 1; score <= 10; score++) {
                distribution.put(score, 0L);
            }
            for (var scoreCount : ratingDao.scoreCounts(connection)) {
                distribution.put(scoreCount.score(), scoreCount.count());
            }
            return new DashboardSnapshot(counts, ratingDao.topRatedEpisodes(connection), watchProgressDao.mostWatchedSeries(connection),
                    Map.copyOf(distribution));
        });
    }
}
