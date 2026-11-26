package com.dippie.service.movie;

import com.dippie.model.movie.Movie;
import com.dippie.repository.movie.MovieRepository;
import com.dippie.service.catalog.CatalogVisibility;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

// cổng check phim có đang mở công khai không dựa theo list phim ẩn của danh mục
@Component
public class MovieVisibility {

    private final MovieRepository movieRepository;
    private final CatalogVisibility catalogVisibility;

    public MovieVisibility(MovieRepository movieRepository, CatalogVisibility catalogVisibility) {
        this.movieRepository = movieRepository;
        this.catalogVisibility = catalogVisibility;
    }

    // lấy phim theo mã, nằm trong list ẩn thì bỏ qua luôn
    @Transactional(readOnly = true)
    public Optional<Movie> findVisibleById(String movieId) {
        if (movieId == null) {
            return Optional.empty();
        }
        return findVisibleById(movieId, catalogVisibility.hiddenMovieIds());
    }

    // xài lại tập hiddenIds đã kéo sẵn cho đỡ đọc cache lần nữa, vẫn lọc lại sau query phòng DB phân biệt hoa thường ID khác nhau
    public Optional<Movie> findVisibleById(String movieId, Set<String> hiddenIds) {
        return movieId == null || hiddenIds.contains(movieId) ? Optional.empty()
                : movieRepository.findById(movieId).filter(movie -> !hiddenIds.contains(movie.getId()));
    }
}
