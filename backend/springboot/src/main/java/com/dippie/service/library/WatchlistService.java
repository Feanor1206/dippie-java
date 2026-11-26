package com.dippie.service.library;

import com.dippie.model.library.WatchlistItem;
import com.dippie.repository.library.WatchlistItemRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.service.catalog.CatalogReadModelCache;
import com.dippie.service.movie.MovieVisibility;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.LocalDateTime;

// Service quản lý danh sách xem sau, cài newEntry() của lớp cha để trả về WatchlistItem.
@Service
public class WatchlistService extends AbstractLibraryListService<WatchlistItem> {

    public WatchlistService(
            WatchlistItemRepository repository,
            MovieVisibility movieVisibility,
            CatalogReadModelCache catalogReadModelCache,
            CurrentViewer currentViewer,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        super(repository, movieVisibility, catalogReadModelCache, currentViewer, transactionManager, clock);
    }

    @Override
    protected WatchlistItem newEntry(Long userId, String movieId, LocalDateTime addedAt) {
        return new WatchlistItem(userId, movieId, addedAt);
    }
}
