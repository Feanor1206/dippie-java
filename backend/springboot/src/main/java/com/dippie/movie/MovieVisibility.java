package com.dippie.movie;

import com.dippie.catalog.query.CatalogVisibility;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

@Component
public class MovieVisibility {

    private final MovieRepository movieRepository;
    private final CatalogVisibility catalogVisibility;

    public MovieVisibility(MovieRepository movieRepository, CatalogVisibility catalogVisibility) {
        this.movieRepository = movieRepository;
        this.catalogVisibility = catalogVisibility;
    }

    public Set<String> hiddenMovieIds() {
        return catalogVisibility.hiddenMovieIds();
    }

    public boolean isVisible(String movieId) {
        return catalogVisibility.isMovieVisible(movieId);
    }

    public Optional<Movie> findVisibleById(String movieId) {
        if (movieId == null) {
            return Optional.empty();
        }
        return findVisibleById(movieId, hiddenMovieIds());
    }

    Optional<Movie> findVisibleById(String movieId, Set<String> hiddenIds) {
        return movieId == null || hiddenIds.contains(movieId) ? Optional.empty()
                : movieRepository.findById(movieId).filter(movie -> !hiddenIds.contains(movie.getId()));
    }
}
