package com.dippie.library;

import java.time.LocalDateTime;

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
