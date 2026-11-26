package com.dippie.repository.library;

import com.dippie.model.library.WatchlistItem;

// Repo cho bảng watchlist_items, kế thừa toàn bộ truy vấn chung từ LibraryEntryRepository.
public interface WatchlistItemRepository extends LibraryEntryRepository<WatchlistItem> {
}
