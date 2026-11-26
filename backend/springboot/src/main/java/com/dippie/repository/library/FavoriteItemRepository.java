package com.dippie.repository.library;

import com.dippie.model.library.FavoriteItem;

// repo bảng favorite_items, hưởng trọn mấy câu query chung từ LibraryEntryRepository
public interface FavoriteItemRepository extends LibraryEntryRepository<FavoriteItem> {
}
