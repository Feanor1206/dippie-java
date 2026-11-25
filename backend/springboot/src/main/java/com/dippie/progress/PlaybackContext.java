package com.dippie.progress;

import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.catalog.domain.CatalogSeason;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.media.MediaAsset;

public record PlaybackContext(
        CatalogEpisode episode,
        CatalogSeries series,
        CatalogSeason season,
        CatalogMediaAsset catalogAsset,
        MediaAsset mediaAsset
) {

    public WatchDuration duration() {
        return WatchDuration.resolve(episode.getDurationMs(), catalogAsset.getDurationMs());
    }
}
