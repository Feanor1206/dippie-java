package com.dippie.library;

import com.dippie.catalog.query.CatalogReadModelCache;
import com.dippie.movie.MovieVisibility;
import com.dippie.security.CurrentViewer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.LocalDateTime;

// Service quản lý danh sách phim yêu thích, chỉ cần cài newEntry() để tạo FavoriteItem cho luồng chung ở lớp cha.
@Service
public class FavoriteService extends AbstractLibraryListService<FavoriteItem> {

    public FavoriteService(
            FavoriteItemRepository repository,
            MovieVisibility movieVisibility,
            CatalogReadModelCache catalogReadModelCache,
            CurrentViewer currentViewer,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        super(repository, movieVisibility, catalogReadModelCache, currentViewer, transactionManager, clock);
    }

    @Override
    protected FavoriteItem newEntry(Long userId, String movieId, LocalDateTime addedAt) {
        return new FavoriteItem(userId, movieId, addedAt);
    }
}
