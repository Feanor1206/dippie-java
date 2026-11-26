package com.dippie.dto.catalog;

import java.util.List;

// chi tiết một bộ phim kèm list mùa với mấy tập hợp lệ
public record CatalogSeriesDetail(CatalogSeriesView series, List<CatalogSeasonView> seasons,
        List<CatalogEpisodeView> episodes) {

    public CatalogSeriesDetail {
        seasons = seasons == null ? List.of() : List.copyOf(seasons);
        episodes = episodes == null ? List.of() : List.copyOf(episodes);
    }
}
