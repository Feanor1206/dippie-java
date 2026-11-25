package com.dippie.library;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MovieRatingRepository extends JpaRepository<MovieRating, Long> {

    List<MovieRating> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<MovieRating> findByUserIdAndMovieId(Long userId, String movieId);

    void deleteByUserIdAndMovieId(Long userId, String movieId);
}
