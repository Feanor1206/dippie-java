package com.dippie.dto.library;

import com.dippie.model.library.LibraryEntry;
import com.dippie.model.movie.Movie;

import java.time.LocalDateTime;

// view một phim trong danh sách yêu thích hay xem sau
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
