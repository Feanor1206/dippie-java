package com.dippie.movie;

import com.dippie.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.util.List;

// API công khai để tìm kiếm, duyệt phân trang, liệt kê thể loại và xem chi tiết phim.
@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieQueryService movieQueryService;

    public MovieController(MovieQueryService movieQueryService) {
        this.movieQueryService = movieQueryService;
    }

    // Nhận cả ba tên tham số search/query/q vì các trang cũ ở frontend gọi không giống nhau; số bản ghi trả về bị chặn trần trong service.
    @GetMapping
    public ApiResponse<List<MovieView>> listMovies(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.ok(movieQueryService.listMovies(search, query, q, genre, limit));
    }

    @GetMapping("/browse")
    public ApiResponse<MovieBrowsePage> browseMovies(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String genres,
            @RequestParam(required = false) Boolean animationOnly,
            @RequestParam(required = false) Integer yearMin,
            @RequestParam(required = false) Integer yearMax,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") BigInteger page,
            @RequestParam(defaultValue = "12") int size
    ) {
        MovieBrowseQuery browseQuery = MovieBrowseQuery.of(query, search, q, genre, genres, animationOnly,
                yearMin, yearMax, sort, direction, page, size);
        return ApiResponse.ok(movieQueryService.browseMovies(browseQuery));
    }

    @GetMapping("/genres")
    public List<MovieGenreView> listGenres() {
        return movieQueryService.listGenres();
    }

    @GetMapping("/{movieId}")
    public ApiResponse<MovieView> getMovieById(@PathVariable String movieId) {
        return ApiResponse.ok(movieQueryService.getMovieById(movieId));
    }
}
