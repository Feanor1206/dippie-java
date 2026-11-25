package com.dippie.catalog.query;

import com.dippie.catalog.domain.CatalogEpisode;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

@Component
public class CatalogVisibility {

    private final CatalogReadModelCache cache;

    public CatalogVisibility(CatalogReadModelCache cache) {
        this.cache = cache;
    }

    public boolean isSeriesVisible(String id) {
        return cache.current().visibleSeriesIds().contains(id);
    }

    boolean isEpisodeVisible(CatalogEpisode episode) {
        return cache.current().visibleEpisodeIds().contains(episode.getId());
    }

    public Set<String> hiddenMovieIds() {
        return cache.current().hiddenMovieIds();
    }

    static boolean followsPublishedParents(String seriesId, String seasonId,
            Predicate<String> visibleSeries, Function<String, String> publishedSeasonParent) {
        return visibleSeries.test(seriesId)
                && (seasonId == null || seasonId.isEmpty() || seriesId.equals(publishedSeasonParent.apply(seasonId)));
    }
}
