package com.dippie.library;

import com.dippie.common.error.InvalidRequestException;
import com.dippie.movie.Movie;
import com.dippie.movie.MovieVisibility;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class MovieRatingService {

    private final MovieRatingRepository ratingRepository;
    private final MovieVisibility movieVisibility;
    private final CurrentViewer currentViewer;
    private final TransactionTemplate requiresNewTransaction;
    private final TransactionTemplate readTransaction;
    private final Clock clock;

    public MovieRatingService(
            MovieRatingRepository ratingRepository,
            MovieVisibility movieVisibility,
            CurrentViewer currentViewer,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.ratingRepository = ratingRepository;
        this.movieVisibility = movieVisibility;
        this.currentViewer = currentViewer;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.clock = clock;
    }

    public List<MovieRatingView> list() {
        Viewer viewer = currentViewer.require();
        return readTransaction.execute(status -> ratingRepository.findByUserIdOrderByUpdatedAtDesc(viewer.userId())
                .stream()
                .map(MovieRatingView::from)
                .toList());
    }

    public MovieRatingView rate(String rawMovieId, Integer rating) {
        Viewer viewer = currentViewer.require();
        String movieId = rawMovieId == null ? "" : rawMovieId.strip();
        if (movieId.isEmpty()) {
            throw new InvalidRequestException(MovieRating.INVALID_RATING_MESSAGE);
        }
        MovieRating.validateRating(rating);
        Movie movie = movieVisibility.findVisibleById(movieId)
                .orElseThrow(() -> new InvalidRequestException("Phim không tồn tại trong danh mục: " + movieId));

        MovieRating saved;
        try {
            saved = requiresNewTransaction.execute(status -> upsertRating(viewer.userId(), movie.getId(), rating));
        } catch (DataIntegrityViolationException ex) {
            saved = requiresNewTransaction.execute(status -> updateExistingRating(viewer.userId(), movie.getId(), rating, ex));
        }
        return MovieRatingView.from(saved);
    }

    public void remove(String movieId) {
        Viewer viewer = currentViewer.require();
        requiresNewTransaction.executeWithoutResult(status -> ratingRepository.deleteByUserIdAndMovieId(viewer.userId(), movieId));
    }

    private MovieRating upsertRating(Long userId, String movieId, int rating) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        return ratingRepository.findByUserIdAndMovieId(userId, movieId)
                .map(existing -> {
                    existing.updateRating(rating, now);
                    return ratingRepository.saveAndFlush(existing);
                })
                .orElseGet(() -> ratingRepository.saveAndFlush(new MovieRating(userId, movieId, rating, now)));
    }

    private MovieRating updateExistingRating(
            Long userId,
            String movieId,
            int rating,
            DataIntegrityViolationException original
    ) {
        MovieRating existing = ratingRepository.findByUserIdAndMovieId(userId, movieId)
                .orElseThrow(() -> original);
        existing.updateRating(rating, LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
        return ratingRepository.saveAndFlush(existing);
    }
}
