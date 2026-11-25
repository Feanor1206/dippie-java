package com.dippie.desktop.model.catalog;

import com.dippie.desktop.model.SearchText;

import java.util.List;

// Giao diện gốc cho cây danh mục theo mẫu Composite (sealed interface chỉ cho phép 3 cấp: bộ, mùa, tập).
// Nhờ nó mà cây giao diện và bộ lọc duyệt cả 3 loại nút theo cùng một kiểu.
public sealed interface CatalogNode permits SeriesNode, SeasonNode, EpisodeNode {
    String id();

    String title();

    String synopsis();

    default List<? extends CatalogNode> children() {
        return List.of();
    }

    default boolean matches(String term) {
        return SearchText.contains(title(), term) || SearchText.contains(id(), term);
    }
}
