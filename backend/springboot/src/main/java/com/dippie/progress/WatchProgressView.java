package com.dippie.progress;

import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogSeason;
import com.dippie.catalog.domain.CatalogSeries;

import java.time.LocalDateTime;

// DTO trả tiến độ xem của một tập phim ra frontend kèm cờ completed và số version để gửi lại ở lần lưu sau.
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
