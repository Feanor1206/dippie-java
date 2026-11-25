package com.dippie.library;

import com.dippie.common.web.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class LibraryController {

    private final WatchlistService watchlistService;
    private final FavoriteService favoriteService;
    private final MovieRatingService movieRatingService;

    public LibraryController(
            WatchlistService watchlistService,
            FavoriteService favoriteService,
            MovieRatingService movieRatingService
    ) {
        this.watchlistService = watchlistService;
        this.favoriteService = favoriteService;
        this.movieRatingService = movieRatingService;
    }

    @GetMapping("/watchlist")
    public ApiResponse<List<LibraryItemView>> getWatchlist() {
        return ApiResponse.ok(watchlistService.list());
    }

    @PostMapping("/watchlist")
    public ApiResponse<LibraryItemView> addWatchlist(@RequestBody MovieIdRequest request) {
        String movieId = request == null ? null : request.movieId();
        return ApiResponse.ok(watchlistService.add(movieId), "Đã thêm phim vào danh sách xem sau");
    }

    @DeleteMapping("/watchlist/{movieId}")
    public ApiResponse<Void> removeWatchlist(@PathVariable String movieId) {
        watchlistService.remove(movieId);
        return ApiResponse.ok(null, "Đã xóa phim khỏi danh sách xem sau");
    }

    @GetMapping("/favorites")
    public ApiResponse<List<LibraryItemView>> getFavorites() {
        return ApiResponse.ok(favoriteService.list());
    }

    @PostMapping("/favorites")
    public ApiResponse<LibraryItemView> addFavorite(@RequestBody MovieIdRequest request) {
        String movieId = request == null ? null : request.movieId();
        return ApiResponse.ok(favoriteService.add(movieId), "Đã thêm phim vào danh sách yêu thích");
    }

    @DeleteMapping("/favorites/{movieId}")
    public ApiResponse<Void> removeFavorite(@PathVariable String movieId) {
        favoriteService.remove(movieId);
        return ApiResponse.ok(null, "Đã xóa phim khỏi danh sách yêu thích");
    }

    @GetMapping("/ratings")
    public ApiResponse<List<MovieRatingView>> getRatings() {
        return ApiResponse.ok(movieRatingService.list());
    }

    @PostMapping("/ratings")
    public ApiResponse<MovieRatingView> addRating(@RequestBody RatingRequest request) {
        String movieId = request == null ? null : request.movieId();
        Integer rating = request == null ? null : request.rating();
        return ApiResponse.ok(movieRatingService.rate(movieId, rating), "Đã ghi nhận đánh giá phim");
    }

    @DeleteMapping("/ratings/{movieId}")
    public ApiResponse<Void> removeRating(@PathVariable String movieId) {
        movieRatingService.remove(movieId);
        return ApiResponse.ok(null, "Đã xóa đánh giá phim");
    }
}
