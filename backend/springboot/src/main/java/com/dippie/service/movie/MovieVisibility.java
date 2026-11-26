package com.dippie.service.movie;

import com.dippie.model.movie.Movie;
import com.dippie.repository.movie.MovieRepository;
import com.dippie.service.catalog.CatalogVisibility;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

// Cổng kiểm tra phim có đang mở công khai hay không dựa trên danh sách phim ẩn của danh mục.
@Component
public class MovieVisibility {

    private final MovieRepository movieRepository;
    private final CatalogVisibility catalogVisibility;

    public MovieVisibility(MovieRepository movieRepository, CatalogVisibility catalogVisibility) {
        this.movieRepository = movieRepository;
        this.catalogVisibility = catalogVisibility;
    }

    // Lấy phim theo mã và tự loại bỏ nếu mã nằm trong danh sách ẩn hiện tại.
    @Transactional(readOnly = true)
    public Optional<Movie> findVisibleById(String movieId) {
        if (movieId == null) {
            return Optional.empty();
        }
        return findVisibleById(movieId, catalogVisibility.hiddenMovieIds());
    }

    // Dùng tập hiddenIds đã tải sẵn trong cùng luồng gọi để đỡ đọc lại từ cache; lọc lại lần nữa sau khi truy vấn phòng trường hợp ID phân biệt hoa thường khác nhau ở DB.
    public Optional<Movie> findVisibleById(String movieId, Set<String> hiddenIds) {
        return movieId == null || hiddenIds.contains(movieId) ? Optional.empty()
                : movieRepository.findById(movieId).filter(movie -> !hiddenIds.contains(movie.getId()));
    }
}
