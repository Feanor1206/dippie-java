package com.dippie.desktop.dao;

import com.dippie.desktop.db.JdbcDatabase;
import com.dippie.desktop.model.Movie;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

// DAO đọc bảng movies để tra tên phim cho màn hình kiểm duyệt đánh giá
public final class MovieDao extends AbstractJdbcDao<Movie, String> {
    private static final RowMapper<Movie> MAPPER =
            row -> new Movie(row.getString("id"), row.getString("title"));

    public MovieDao(JdbcDatabase db) {
        super(db, "movies", "id, title", MAPPER);
    }

    // chỉ tải tên mấy phim thật sự có đánh giá (kể cả phim cũ ngoài danh mục) cho gọn truy vấn
    public List<Movie> findRatedMovies(Connection connection) throws SQLException {
        return query(connection, """
                SELECT m.id, m.title FROM movies m
                WHERE EXISTS (SELECT 1 FROM movie_ratings r WHERE r.movie_id = m.id)
                ORDER BY m.id
                """, MAPPER);
    }
}
