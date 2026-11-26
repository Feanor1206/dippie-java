package com.dippie.desktop.model.catalog;

import com.dippie.desktop.model.SearchText;

import java.util.List;

// nút gốc cấp bộ phim trong cây Composite, chứa danh sách mùa và điểm trung bình cả bộ
public record SeriesNode(String id, String title, String originalTitle, String synopsis,
                         Double averageRating, List<SeasonNode> children) implements CatalogNode {
    public SeriesNode {
        children = List.copyOf(children);
    }

    // khớp tên bộ hay tên gốc thì giữ trọn nhánh, không thì lọc sâu xuống từng mùa
    public SeriesNode filter(String term) {
        if (matches(term) || SearchText.contains(originalTitle, term)) {
            return this;
        }
        var matching = children.stream().map(season -> season.filter(term)).filter(java.util.Objects::nonNull).toList();
        return matching.isEmpty() ? null : new SeriesNode(id, title, originalTitle, synopsis, averageRating, matching);
    }
}
