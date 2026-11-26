package com.dippie.desktop.model.catalog;

import com.dippie.desktop.model.SearchText;

import java.util.List;

// interface gốc của cây danh mục kiểu Composite (sealed interface chốt đúng 3 cấp bộ, mùa, tập)
// nhờ vậy cây giao diện với bộ lọc duyệt cả 3 loại nút theo cùng 1 kiểu
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
