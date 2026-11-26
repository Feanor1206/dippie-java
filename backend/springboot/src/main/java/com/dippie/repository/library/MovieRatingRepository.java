package com.dippie.repository.library;

import com.dippie.model.library.MovieRating;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// repo bảng movie_ratings, vừa đọc điểm cá nhân vừa gom điểm trung bình cộng đồng
public interface MovieRatingRepository extends JpaRepository<MovieRating, Long> {

    List<MovieRating> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<MovieRating> findByUserIdAndMovieId(Long userId, String movieId);

    @Query("select rating.movieId from MovieRating rating where rating.userId = :userId")
    List<String> findMovieIdsByUserId(@Param("userId") Long userId);

    void deleteByUserIdAndMovieId(Long userId, String movieId);

    // xóa tài khoản thì dọn sạch lượt chấm cũ của user
    void deleteByUserId(Long userId);

    // gom điểm trung bình với số lượt chấm cho cả lô movieId trong một câu SQL
    @Query("""
            select new com.dippie.repository.library.MovieRatingRepository$RatingRow(rating.movieId, avg(rating.rating), count(rating))
            from MovieRating rating where rating.movieId in :ids group by rating.movieId
            """)
    List<RatingRow> summarize(@Param("ids") List<String> ids);

    // projection hứng điểm trung bình với số lượt chấm của từng phim
    record RatingRow(String id, double average, long count) {
    }
}
