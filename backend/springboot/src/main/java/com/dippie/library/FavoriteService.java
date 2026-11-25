package com.dippie.library;

import com.dippie.movie.MovieVisibility;
import com.dippie.security.CurrentViewer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class FavoriteService extends AbstractLibraryListService<FavoriteItem> {

    private final FavoriteItemRepository repository;

    public FavoriteService(
            FavoriteItemRepository repository,
            MovieVisibility movieVisibility,
            CurrentViewer currentViewer,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        super(movieVisibility, currentViewer, transactionManager, clock);
        this.repository = repository;
    }

    @Override
    protected List<LibraryItemView> findViewsByUserId(Long userId) {
        return repository.findViewsByUserIdOrderByAddedAtDesc(userId);
    }

    @Override
    protected Optional<FavoriteItem> findExisting(Long userId, String movieId) {
        return repository.findByUserIdAndMovieId(userId, movieId);
    }

    @Override
    protected FavoriteItem newEntry(Long userId, String movieId, LocalDateTime addedAt) {
        return new FavoriteItem(userId, movieId, addedAt);
    }

    @Override
    protected FavoriteItem saveEntry(FavoriteItem entry) {
        return repository.saveAndFlush(entry);
    }

    @Override
    protected void deleteEntry(Long userId, String movieId) {
        repository.deleteByUserIdAndMovieId(userId, movieId);
    }
}
