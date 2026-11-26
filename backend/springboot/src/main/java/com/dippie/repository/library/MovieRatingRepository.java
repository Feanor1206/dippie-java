package com.dippie.repository.library;

import com.dippie.model.library.MovieRating;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// Repo cho bảng movie_ratings, dùng cho cả đánh giá cá nhân lẫn tổng hợp điểm trung bình cộng đồng.
public interface MovieRatingRepository extends JpaRepository<MovieRating, Long> {

    List<MovieRating> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<MovieRating> findByUserIdAndMovieId(Long userId, String movieId);

    @Query("select rating.movieId from MovieRating rating where rating.userId = :userId")
    List<String> findMovieIdsByUserId(@Param("userId") Long userId);

    void deleteByUserIdAndMovieId(Long userId, String movieId);

    // Gọi khi xóa tài khoản để dọn sạch đánh giá cũ của user.
    void deleteByUserId(Long userId);

    // Gom điểm trung bình và tổng lượt chấm cho một lô movieId trong một câu SQL.
    @Query("""
            select new com.dippie.repository.library.MovieRatingRepository$RatingRow(rating.movieId, avg(rating.rating), count(rating))
            from MovieRating rating where rating.movieId in :ids group by rating.movieId
            """)
    List<RatingRow> summarize(@Param("ids") List<String> ids);

    // Kết quả chiếu cho từng phim khi gom điểm trung bình và số lượt đánh giá.
    record RatingRow(String id, double average, long count) {
    }
}
