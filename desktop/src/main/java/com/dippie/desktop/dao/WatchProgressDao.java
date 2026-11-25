package com.dippie.desktop.dao;

import com.dippie.desktop.db.JdbcDatabase;
import com.dippie.desktop.model.WatchProgress;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

// DAO chỉ đọc bảng watch_progress để thống kê lượt xem theo từng bộ phim.
public final class WatchProgressDao extends AbstractJdbcDao<WatchProgress, Long> {
    public WatchProgressDao(JdbcDatabase db) {
        super(db, "watch_progress", "id, user_id, episode_id, position_ms",
                row -> new WatchProgress(row.getLong("id"), row.getLong("user_id"),
                        row.getString("episode_id"), row.getLong("position_ms")));
    }

    // Mỗi tài khoản tính một lần cho mỗi bộ khi đã có position_ms > 0, lấy top 10 bộ nhiều người xem nhất.
    public List<MostWatched> mostWatchedSeries(Connection connection) throws SQLException {
        return query(connection, """
                SELECT s.id, s.title, COUNT(DISTINCT p.user_id) AS viewers
                FROM watch_progress p
                JOIN catalog_episodes e ON e.id = p.episode_id
                JOIN catalog_series s ON s.id = e.series_id
                WHERE p.position_ms > 0
                GROUP BY s.id, s.title
                ORDER BY viewers DESC, s.id
                LIMIT 10
                """, row -> new MostWatched(row.getString("id"), row.getString("title"), row.getLong("viewers")));
    }

    // Một dòng trong bảng các bộ phim nhiều người xem nhất.
    public record MostWatched(String seriesId, String title, long viewers) {
    }
}
