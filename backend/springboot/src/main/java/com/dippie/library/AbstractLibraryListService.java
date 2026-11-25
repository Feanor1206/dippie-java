package com.dippie.library;

import com.dippie.catalog.query.CatalogReadModel;
import com.dippie.catalog.query.CatalogReadModelCache;
import com.dippie.catalog.query.SeriesCard;
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
import java.util.Set;

// Lớp dịch vụ gốc dùng Template Method và Factory Method qua newEntry() để FavoriteService với WatchlistService dùng chung luồng xem, thêm và xóa.
public abstract class AbstractLibraryListService<E extends LibraryEntry> {

    private final LibraryEntryRepository<E> repository;
    private final MovieVisibility movieVisibility;
    private final CatalogReadModelCache catalogReadModelCache;
    private final CurrentViewer currentViewer;
    private final TransactionTemplate requiresNewTransaction;
    private final TransactionTemplate readTransaction;
    private final Clock clock;

    protected AbstractLibraryListService(
            LibraryEntryRepository<E> repository,
            MovieVisibility movieVisibility,
            CatalogReadModelCache catalogReadModelCache,
            CurrentViewer currentViewer,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.repository = repository;
        this.movieVisibility = movieVisibility;
        this.catalogReadModelCache = catalogReadModelCache;
        this.currentViewer = currentViewer;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.clock = clock;
    }

    // Lấy danh sách phim của user theo thứ tự mới thêm nhất, ẩn các phim chưa xuất bản và lấy poster từ SeriesCard nếu bảng movies để trống.
    public List<LibraryItemView> list() {
        Viewer viewer = currentViewer.require();
        CatalogReadModel model = catalogReadModelCache.current();
        Set<String> hiddenIds = model.hiddenMovieIds();
        List<LibraryItemView> rawItems = readTransaction.execute(status -> repository.findViewsByUserIdOrderByAddedAtDesc(viewer.userId()));
        // Lọc phim bị ẩn theo snapshot danh mục rồi bù poster từ SeriesCard cho mấy mục bị thiếu ảnh
        return rawItems.stream()
                .filter(item -> !hiddenIds.contains(item.movieId()))
                .map(item -> enrichArtwork(item, model))
                .toList();
    }

    private static LibraryItemView enrichArtwork(LibraryItemView item, CatalogReadModel model) {
        if (item.posterUrl() != null && !item.posterUrl().isBlank()) {
            return item;
        }
        SeriesCard card = model.seriesCard(item.movieId());
        if (card == null || card.posterUrl() == null || card.posterUrl().isBlank()) {
            return item;
        }
        return new LibraryItemView(item.id(), item.userId(), item.movieId(), item.movieTitle(), card.posterUrl(), item.addedAt());
    }

    // Thêm phim theo kiểu gọi lại nhiều lần vẫn ra đúng một bản ghi.
    // Việt nhớ giữ REQUIRES_NEW cho nhánh lưu nhé: hai request bấm thêm cùng lúc mà đụng unique key thì chỉ giao dịch con rollback, mình bắt DataIntegrityViolationException rồi đọc lại bản ghi vừa thắng.
    public LibraryItemView add(String rawMovieId) {
        Viewer viewer = currentViewer.require();
        String movieId = rawMovieId == null ? "" : rawMovieId.strip();
        if (movieId.isEmpty()) {
            throw new InvalidRequestException("Bạn chưa chọn phim.");
        }
        // Kiểm tra phim có đang mở công khai không và xem user đã lưu sẵn chưa trong cùng một giao dịch đọc
        LookupResult<E> lookup = readTransaction.execute(status -> {
            Movie movie = movieVisibility.findVisibleById(movieId)
                    .orElseThrow(() -> new InvalidRequestException("Không tìm thấy phim: " + movieId));
            return new LookupResult<>(movie, repository.findByUserIdAndMovieId(viewer.userId(), movie.getId()));
        });
        Movie movie = lookup.movie();
        if (lookup.existing().isPresent()) {
            return LibraryItemView.of(lookup.existing().get(), movie);
        }

        // Chèn bản ghi mới trong giao dịch riêng; nếu request song song đã chèn trước thì đọc lại dòng đó để trả về bình thường
        E entry;
        try {
            entry = requiresNewTransaction.execute(status -> repository.saveAndFlush(
                    newEntry(viewer.userId(), movie.getId(), LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS))
            ));
        } catch (DataIntegrityViolationException ex) {
            entry = readTransaction.execute(status -> repository.findByUserIdAndMovieId(viewer.userId(), movie.getId()))
                    .orElseThrow(() -> ex);
        }
        return LibraryItemView.of(entry, movie);
    }

    public void remove(String movieId) {
        Viewer viewer = currentViewer.require();
        requiresNewTransaction.executeWithoutResult(status -> repository.deleteByUserIdAndMovieId(viewer.userId(), movieId));
    }

    // Factory Method để lớp con trả về đúng entity FavoriteItem hoặc WatchlistItem.
    protected abstract E newEntry(Long userId, String movieId, LocalDateTime addedAt);

    // Gói phim tìm thấy và mục đã lưu (nếu có) để trả ra từ cùng một giao dịch đọc.
    private record LookupResult<E extends LibraryEntry>(Movie movie, Optional<E> existing) {
    }
}
