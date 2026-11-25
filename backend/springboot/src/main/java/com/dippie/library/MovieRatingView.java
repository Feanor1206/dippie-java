package com.dippie.library;

import java.time.LocalDateTime;

// DTO trả thông tin đánh giá phim của một tài khoản ra API.
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
