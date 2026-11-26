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

    List<MovieRating> findByUserIdAndProfileScopeIdOrderByUpdatedAtDesc(Long userId, long profileScopeId);

    Optional<MovieRating> findByUserIdAndProfileScopeIdAndMovieId(Long userId, long profileScopeId, String movieId);

    // bản cũ tra theo hồ sơ chính (profileScopeId = 0) để giữ tương thích test cũ
    @Query("select rating from MovieRating rating where rating.userId = :userId and rating.profileScopeId = 0 and rating.movieId = :movieId")
    Optional<MovieRating> findByUserIdAndMovieId(@Param("userId") Long userId, @Param("movieId") String movieId);

    @Query("select rating.movieId from MovieRating rating where rating.userId = :userId and rating.profileScopeId = :profileScopeId")
    List<String> findMovieIdsByUserIdAndProfileScopeId(@Param("userId") Long userId, @Param("profileScopeId") long profileScopeId);

    void deleteByUserIdAndProfileScopeIdAndMovieId(Long userId, long profileScopeId, String movieId);

    // xóa riêng điểm đánh giá của 1 hồ sơ trẻ em khi xóa hồ sơ đó
    void deleteByUserIdAndProfileScopeId(Long userId, long profileScopeId);

    // xóa tài khoản thì dọn sạch lượt chấm cũ của cả hồ sơ chính lẫn hồ sơ bé
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
