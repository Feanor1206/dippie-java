package com.dippie.dto.progress;

import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogSeason;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.progress.PlaybackContext;
import com.dippie.model.progress.WatchDuration;
import com.dippie.model.progress.WatchProgress;

import java.time.LocalDateTime;

// tiến độ xem của một tập trả frontend kèm cờ completed với version để lần sau gửi lại
public record WatchProgressView(
        String episodeId,
        String seriesId,
        String seriesTitle,
        String seasonId,
        Integer seasonNumber,
        String seasonTitle,
        Integer episodeNumber,
        String episodeTitle,
        String assetId,
        long positionMs,
        Long durationMs,
        boolean completed,
        long version,
        LocalDateTime updatedAt,
        String thumbnailUrl
) {

    public static WatchProgressView of(WatchProgress progress, PlaybackContext context, String thumbnailUrl) {
        CatalogEpisode episode = context.episode();
        CatalogSeries series = context.series();
        CatalogSeason season = context.season();
        WatchDuration duration = context.duration();
        return new WatchProgressView(
                episode.getId(),
                series.getId(),
                series.getTitle(),
                season == null ? null : season.getId(),
                season == null ? null : season.getNumber(),
                season == null ? null : season.getTitle(),
                episode.getNumber(),
                episode.getTitle(),
                progress.getAssetId(),
                progress.getPositionMs(),
                duration.nullableDurationMs(),
                duration.isCompletedAt(progress.getPositionMs()),
                progress.getOptLockVersion(),
                progress.getUpdatedAt(),
                thumbnailUrl
        );
    }
}
