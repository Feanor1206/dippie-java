package com.dippie.repository.library;

import com.dippie.model.library.WatchlistItem;

// repo bảng watchlist_items, hưởng trọn mấy câu query chung từ LibraryEntryRepository
public interface WatchlistItemRepository extends LibraryEntryRepository<WatchlistItem> {
}
