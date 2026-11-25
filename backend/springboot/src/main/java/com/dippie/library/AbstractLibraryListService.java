package com.dippie.library;

import com.dippie.common.error.InvalidRequestException;
import com.dippie.movie.Movie;
import com.dippie.movie.MovieVisibility;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

public abstract class AbstractLibraryListService<E extends LibraryEntry> {

    private final MovieVisibility movieVisibility;
    private final CurrentViewer currentViewer;
    private final TransactionTemplate requiresNewTransaction;
    private final TransactionTemplate readTransaction;
    private final Clock clock;

    protected AbstractLibraryListService(
            MovieVisibility movieVisibility,
            CurrentViewer currentViewer,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.movieVisibility = movieVisibility;
        this.currentViewer = currentViewer;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.clock = clock;
    }

    public List<LibraryItemView> list() {
        Viewer viewer = currentViewer.require();
        return List.copyOf(readTransaction.execute(status -> findViewsByUserId(viewer.userId())));
    }

    public LibraryItemView add(String rawMovieId) {
        Viewer viewer = currentViewer.require();
        String movieId = rawMovieId == null ? "" : rawMovieId.strip();
        if (movieId.isEmpty()) {
            throw new InvalidRequestException("Movie ID is required");
        }
        Movie movie = movieVisibility.findVisibleById(movieId)
                .orElseThrow(() -> new InvalidRequestException("Phim không tồn tại trong danh mục: " + movieId));

        Optional<E> existing = findExisting(viewer.userId(), movie.getId());
        if (existing.isPresent()) {
            return LibraryItemView.of(existing.get(), movie);
        }

        E entry;
        try {
            entry = requiresNewTransaction.execute(status -> saveEntry(
                    newEntry(viewer.userId(), movie.getId(), LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS))
            ));
        } catch (DataIntegrityViolationException ex) {
            entry = readTransaction.execute(status -> findExisting(viewer.userId(), movie.getId()))
                    .orElseThrow(() -> ex);
        }
        return LibraryItemView.of(entry, movie);
    }

    public void remove(String movieId) {
        Viewer viewer = currentViewer.require();
        requiresNewTransaction.executeWithoutResult(status -> deleteEntry(viewer.userId(), movieId));
    }

    protected abstract List<LibraryItemView> findViewsByUserId(Long userId);

    protected abstract Optional<E> findExisting(Long userId, String movieId);

    protected abstract E newEntry(Long userId, String movieId, LocalDateTime addedAt);

    protected abstract E saveEntry(E entry);

    protected abstract void deleteEntry(Long userId, String movieId);
}
