package com.dippie.desktop.model.catalog;

import java.util.List;

// nút cấp mùa trong cây Composite, kiêm luôn nhóm gom mấy tập lẻ chưa xếp mùa (lúc đó id là null)
public record SeasonNode(String id, String title, String synopsis,
                         List<EpisodeNode> children) implements CatalogNode {
    public SeasonNode {
        children = List.copyOf(children);
    }

    // khớp tên mùa thì giữ cả mùa, không thì chỉ giữ lại mấy tập con khớp từ khóa
    public SeasonNode filter(String term) {
        if (matches(term)) {
            return this;
        }
        var matching = children.stream().filter(episode -> episode.matches(term)).toList();
        return matching.isEmpty() ? null : new SeasonNode(id, title, synopsis, matching);
    }
}
