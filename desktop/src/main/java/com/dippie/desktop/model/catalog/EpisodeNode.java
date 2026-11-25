package com.dippie.desktop.model.catalog;

import java.util.List;

// Nút lá của cây Composite (tập phim), giữ thời lượng, điểm trung bình và danh sách nguồn phát.
public record EpisodeNode(String id, String title, String synopsis, Long durationMs,
                          Double averageRating, List<String> sources) implements CatalogNode {
    public EpisodeNode {
        sources = List.copyOf(sources);
    }
}
