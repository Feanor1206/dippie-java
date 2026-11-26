package com.dippie.desktop.dao;

import com.dippie.desktop.db.JdbcDatabase;
import com.dippie.desktop.model.Rating;
import com.dippie.desktop.model.TopRated;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// DAO bảng movie_ratings, dùng cho cả tab kiểm duyệt đánh giá lẫn thống kê ở Tổng quan
public final class RatingDao extends AbstractJdbcDao<Rating, Long> {
    public RatingDao(JdbcDatabase db) {
        super(db, "movie_ratings", "id, user_id, movie_id, rating, updated_at", RatingDao::mapRating);
    }

    // COALESCE(e.series_id, r.movie_id) ghép được cả đánh giá theo tập lẫn theo bộ, còn LEFT JOIN giữ lại phim cũ ngoài danh mục
    public List<RatingContext> findContexts(Connection connection) throws SQLException {
        return query(connection, """
                SELECT r.id, r.user_id, r.movie_id, r.rating, r.updated_at, u.username,
                       s.id AS series_id, s.title AS series_title
                FROM movie_ratings r
                JOIN users u ON u.id = r.user_id
                LEFT JOIN catalog_episodes e ON e.id = r.movie_id
                LEFT JOIN catalog_series s ON s.id = COALESCE(e.series_id, r.movie_id)
                ORDER BY r.updated_at DESC, r.id DESC
                """, row -> new RatingContext(mapRating(row), row.getString("username"),
                        row.getString("series_id"), row.getString("series_title")));
    }

    public Optional<Rating> lockRating(Connection connection, long id) throws SQLException {
        return lockById(connection, id);
    }

    public int delete(Connection connection, long id) throws SQLException {
        return update(connection, "DELETE FROM movie_ratings WHERE id = ?", id);
    }

    // tính điểm trung bình theo từng movie_id để gắn riêng cho bộ phim hoặc từng tập trên cây danh mục
    public List<Summary> summaries(Connection connection) throws SQLException {
        return query(connection, """
                SELECT movie_id, AVG(rating) AS average_rating
                FROM movie_ratings GROUP BY movie_id
                """, row -> new Summary(row.getString("movie_id"), row.getDouble("average_rating")));
    }

    // lấy top 10 tập điểm trung bình cao nhất, bằng điểm thì ưu tiên tập nhiều lượt chấm hơn
    public List<TopRated> topRatedEpisodes(Connection connection) throws SQLException {
        return query(connection, """
                SELECT e.id, e.title, s.title AS series_title, AVG(r.rating) AS average_rating, COUNT(*) AS total
                FROM movie_ratings r JOIN catalog_episodes e ON e.id = r.movie_id
                JOIN catalog_series s ON s.id = e.series_id
                GROUP BY e.id, e.title, s.title
                ORDER BY average_rating DESC, total DESC, e.id
                LIMIT 10
                """, row -> new TopRated(row.getString("id"), row.getString("series_title"), row.getString("title"),
                        row.getDouble("average_rating"), row.getLong("total")));
    }

    public List<ScoreCount> scoreCounts(Connection connection) throws SQLException {
        return query(connection, "SELECT rating, COUNT(*) AS total FROM movie_ratings GROUP BY rating ORDER BY rating",
                row -> new ScoreCount(row.getInt("rating"), row.getLong("total")));
    }

    private static Rating mapRating(ResultSet row) throws SQLException {
        return new Rating(row.getLong("id"), row.getLong("user_id"), row.getString("movie_id"),
                row.getInt("rating"), row.getObject("updated_at", LocalDateTime.class));
    }

    // gom đánh giá kèm username và bộ phim liên quan (phim cũ thì seriesId có thể null)
    public record RatingContext(Rating rating, String username, String seriesId, String seriesTitle) {
        public long id() {
            return rating.id();
        }
    }

    // điểm trung bình của 1 bộ phim hoặc tập phim đã có người chấm
    public record Summary(String movieId, double average) {
    }

    // số lượt chấm cho từng mức điểm từ 1 đến 10
    public record ScoreCount(int score, long count) {
    }
}
