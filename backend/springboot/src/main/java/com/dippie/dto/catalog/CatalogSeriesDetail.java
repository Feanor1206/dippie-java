package com.dippie.dto.catalog;

import java.util.List;

// Kết quả chi tiết một bộ phim kèm danh sách mùa và lát cắt tập phim hợp lệ.
public record CatalogSeriesDetail(CatalogSeriesView series, List<CatalogSeasonView> seasons,
        List<CatalogEpisodeView> episodes) {

    public CatalogSeriesDetail {
        seasons = seasons == null ? List.of() : List.copyOf(seasons);
        episodes = episodes == null ? List.of() : List.copyOf(episodes);
    }
}
