package com.dippie.service.library;

import com.dippie.model.library.FavoriteItem;
import com.dippie.repository.library.FavoriteItemRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.service.catalog.CatalogReadModelCache;
import com.dippie.service.movie.MovieVisibility;

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
