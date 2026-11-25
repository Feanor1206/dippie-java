package com.dippie.movie;

import com.dippie.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.util.List;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieQueryService movieQueryService;

    public MovieController(MovieQueryService movieQueryService) {
        this.movieQueryService = movieQueryService;
    }

    @GetMapping
    public ApiResponse<List<MovieView>> listMovies(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String genre
    ) {
        return ApiResponse.ok(movieQueryService.listMovies(search, query, q, genre));
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
            @RequestParam(defaultValue = "rating") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") BigInteger page,
            @RequestParam(defaultValue = "12") int size
    ) {
        MovieBrowseQuery browseQuery = MovieBrowseQuery.of(
                query,
                search,
                q,
                genre,
                genres,
                animationOnly,
                yearMin,
                yearMax,
                sort,
                direction,
                page,
                size
        );
        return ApiResponse.ok(movieQueryService.browseMovies(browseQuery));
    }

    @GetMapping("/{movieId}")
    public ApiResponse<MovieView> getMovieById(@PathVariable String movieId) {
        return ApiResponse.ok(movieQueryService.getMovieById(movieId));
    }

    @GetMapping("/{movieId}/media-descriptor")
    public ApiResponse<MediaDescriptorView> getMediaDescriptor(@PathVariable String movieId) {
        return ApiResponse.ok(movieQueryService.getMediaDescriptor(movieId));
    }
}
