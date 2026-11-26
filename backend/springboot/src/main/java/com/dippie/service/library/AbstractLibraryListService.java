package com.dippie.service.library;

import com.dippie.dto.catalog.SeriesCard;
import com.dippie.dto.library.LibraryItemView;
import com.dippie.exception.InvalidRequestException;
import com.dippie.model.library.LibraryEntry;
import com.dippie.model.movie.Movie;
import com.dippie.repository.library.LibraryEntryRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;
import com.dippie.service.catalog.CatalogReadModel;
import com.dippie.service.catalog.CatalogReadModelCache;
import com.dippie.service.movie.MovieVisibility;

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

// lớp cha dùng Template Method với Factory Method qua newEntry() cho FavoriteService và WatchlistService xài chung luồng xem, thêm, xóa
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

    // lấy list phim mới thêm nhất theo hồ sơ đang chọn, bỏ phim đang ẩn và bù poster từ SeriesCard nếu bảng movies để trống
    public List<LibraryItemView> list() {
        Viewer viewer = currentViewer.require();
        CatalogReadModel model = catalogReadModelCache.current();
        Set<String> hiddenIds = model.hiddenMovieIds();
        List<LibraryItemView> rawItems = readTransaction.execute(status ->
                repository.findViewsByUserIdAndProfileScopeIdOrderByAddedAtDesc(viewer.userId(), viewer.profileScopeId()));
        // lọc phim ẩn theo snapshot danh mục rồi bù poster từ SeriesCard cho mấy mục thiếu ảnh
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

    // thêm phim kiểu idempotent, bấm nhiều lần vẫn ra đúng 1 bản ghi
    // Việt giữ REQUIRES_NEW nhánh lưu nha, đụng unique key thì chỉ transaction con rollback rồi bắt lỗi đọc lại dòng thắng
    public LibraryItemView add(String rawMovieId) {
        Viewer viewer = currentViewer.require();
        String movieId = rawMovieId == null ? "" : rawMovieId.strip();
        if (movieId.isEmpty()) {
            throw new InvalidRequestException("Bạn chưa chọn phim.");
        }
        // check phim có đang mở không và hồ sơ hiện tại đã lưu sẵn chưa trong cùng 1 transaction đọc
        LookupResult<E> lookup = readTransaction.execute(status -> {
            Movie movie = movieVisibility.findVisibleById(movieId)
                    .orElseThrow(() -> new InvalidRequestException("Không tìm thấy phim: " + movieId));
            return new LookupResult<>(movie, repository.findByUserIdAndProfileScopeIdAndMovieId(
                    viewer.userId(), viewer.profileScopeId(), movie.getId()));
        });
        Movie movie = lookup.movie();
        if (lookup.existing().isPresent()) {
            return LibraryItemView.of(lookup.existing().get(), movie);
        }

        // chèn dòng mới trong transaction riêng, lỡ request song song chèn trước rồi thì đọc lại dòng đó trả về bình thường
        E entry;
        try {
            entry = requiresNewTransaction.execute(status -> repository.saveAndFlush(
                    newEntry(viewer.userId(), viewer.profileId(), movie.getId(), LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS))
            ));
        } catch (DataIntegrityViolationException ex) {
            entry = readTransaction.execute(status -> repository.findByUserIdAndProfileScopeIdAndMovieId(
                            viewer.userId(), viewer.profileScopeId(), movie.getId()))
                    .orElseThrow(() -> ex);
        }
        return LibraryItemView.of(entry, movie);
    }

    public void remove(String movieId) {
        Viewer viewer = currentViewer.require();
        requiresNewTransaction.executeWithoutResult(status ->
                repository.deleteByUserIdAndProfileScopeIdAndMovieId(viewer.userId(), viewer.profileScopeId(), movieId));
    }

    // chỗ Factory Method để lớp con trả đúng FavoriteItem hay WatchlistItem gắn profileId
    protected abstract E newEntry(Long userId, Long profileId, String movieId, LocalDateTime addedAt);

    // gói phim tìm được với mục đã lưu để trả ra từ cùng 1 transaction đọc
    private record LookupResult<E extends LibraryEntry>(Movie movie, Optional<E> existing) {
    }
}
