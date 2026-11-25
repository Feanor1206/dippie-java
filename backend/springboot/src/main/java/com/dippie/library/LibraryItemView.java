package com.dippie.library;

import com.dippie.movie.Movie;

import java.time.LocalDateTime;

// DTO trả về cho từng mục phim trong danh sách yêu thích hoặc xem sau.
public record LibraryItemView(
        Long id,
        Long userId,
        String movieId,
        String movieTitle,
        String posterUrl,
        LocalDateTime addedAt
) {

    public static LibraryItemView of(LibraryEntry entry, Movie movie) {
        return new LibraryItemView(
                entry.getId(),
                entry.getUserId(),
                entry.getMovieId(),
                movie.getTitle(),
                movie.getPoster(),
                entry.getAddedAt()
        );
    }
}
