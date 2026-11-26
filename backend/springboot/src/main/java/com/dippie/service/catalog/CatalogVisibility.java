package com.dippie.service.catalog;

import com.dippie.model.catalog.CatalogEpisode;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

// check nhanh bộ phim hay tập phim có được hiện công khai không dựa vào bản chụp trong CatalogReadModelCache
@Component
public class CatalogVisibility {

    private final CatalogReadModelCache cache;

    public CatalogVisibility(CatalogReadModelCache cache) {
        this.cache = cache;
    }

    public boolean isSeriesVisible(String id) {
        return cache.current().visibleSeriesIds().contains(id);
    }

    public boolean isEpisodeVisible(CatalogEpisode episode) {
        return cache.current().visibleEpisodeIds().contains(episode.getId());
    }

    public Set<String> hiddenMovieIds() {
        return cache.current().hiddenMovieIds();
    }

    // tập chỉ tính là công khai khi bộ cha đã xuất bản, có gắn mùa thì mùa đó cũng phải đã xuất bản và thuộc đúng bộ đó
    public static boolean followsPublishedParents(String seriesId, String seasonId,
            Predicate<String> visibleSeries, Function<String, String> publishedSeasonParent) {
        return visibleSeries.test(seriesId)
                && (seasonId == null || seasonId.isEmpty() || seriesId.equals(publishedSeasonParent.apply(seasonId)));
    }
}
