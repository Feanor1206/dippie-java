package com.dippie.dto.library;

import com.dippie.model.library.MovieRating;

import java.time.LocalDateTime;

// view lượt chấm điểm phim của một user trả ra API
public record MovieRatingView(
        Long id,
        Long userId,
        String movieId,
        int rating,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MovieRatingView from(MovieRating rating) {
        return new MovieRatingView(
                rating.getId(),
                rating.getUserId(),
                rating.getMovieId(),
                rating.getRating(),
                rating.getCreatedAt(),
                rating.getUpdatedAt()
        );
    }
}
