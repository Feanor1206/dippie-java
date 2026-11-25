package com.dippie.desktop.model.catalog;

import java.util.List;

// Nút cấp mùa trong cây Composite, cũng dùng làm nhóm gom các tập lẻ chưa xếp mùa (lúc đó id là null).
public record SeasonNode(String id, String title, String synopsis,
                         List<EpisodeNode> children) implements CatalogNode {
    public SeasonNode {
        children = List.copyOf(children);
    }

    // Khớp tên mùa thì giữ nguyên cả mùa, còn không thì chỉ giữ lại những tập con khớp từ khóa.
    public SeasonNode filter(String term) {
        if (matches(term)) {
            return this;
        }
        var matching = children.stream().filter(episode -> episode.matches(term)).toList();
        return matching.isEmpty() ? null : new SeasonNode(id, title, synopsis, matching);
    }
}
