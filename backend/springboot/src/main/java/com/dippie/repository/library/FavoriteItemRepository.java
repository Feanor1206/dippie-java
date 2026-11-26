package com.dippie.repository.library;

import com.dippie.model.library.FavoriteItem;

// Repo cho bảng favorite_items, kế thừa toàn bộ truy vấn chung từ LibraryEntryRepository.
public interface FavoriteItemRepository extends LibraryEntryRepository<FavoriteItem> {
}
