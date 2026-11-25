package com.dippie.progress;

import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogEpisodeRepository;
import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.catalog.domain.CatalogMediaAssetRepository;
import com.dippie.catalog.domain.CatalogSeason;
import com.dippie.catalog.domain.CatalogSeasonRepository;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.domain.CatalogSeriesRepository;
import com.dippie.catalog.domain.PublicationStatus;
import com.dippie.media.MediaAsset;
import com.dippie.media.MediaAssetRepository;
import com.dippie.security.Viewer;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
public class PlaybackContextResolver {

    private final CatalogEpisodeRepository episodeRepository;
    private final CatalogSeriesRepository seriesRepository;
    private final CatalogSeasonRepository seasonRepository;
    private final CatalogMediaAssetRepository catalogMediaAssetRepository;
    private final MediaAssetRepository mediaAssetRepository;

    public PlaybackContextResolver(
            CatalogEpisodeRepository episodeRepository,
            CatalogSeriesRepository seriesRepository,
            CatalogSeasonRepository seasonRepository,
            CatalogMediaAssetRepository catalogMediaAssetRepository,
            MediaAssetRepository mediaAssetRepository
    ) {
        this.episodeRepository = episodeRepository;
        this.seriesRepository = seriesRepository;
        this.seasonRepository = seasonRepository;
        this.catalogMediaAssetRepository = catalogMediaAssetRepository;
        this.mediaAssetRepository = mediaAssetRepository;
    }

    public Optional<PlaybackContext> resolve(String episodeId, String assetId, Viewer viewer) {
        CatalogEpisode episode = episodeRepository.findById(episodeId).orElse(null);
        CatalogSeries series = episode == null
                ? null
                : seriesRepository.findById(episode.getSeriesId()).orElse(null);
        CatalogSeason season = (episode == null || episode.getSeasonId() == null)
                ? null
                : seasonRepository.findById(episode.getSeasonId()).orElse(null);
        CatalogMediaAsset catalogAsset = catalogMediaAssetRepository.findById(assetId).orElse(null);
        MediaAsset mediaAsset = mediaAssetRepository.findById(assetId).orElse(null);
        return validate(episode, series, season, catalogAsset, mediaAsset, episodeId, viewer);
    }

    public Optional<PlaybackContext> validate(
            CatalogEpisode episode,
            CatalogSeries series,
            CatalogSeason season,
            CatalogMediaAsset catalogAsset,
            MediaAsset mediaAsset,
            String episodeId,
            Viewer viewer
    ) {
        if (episode == null || !PublicationStatus.of(episode.getRecordJson()).isPublished()) {
            return Optional.empty();
        }
        if (series == null || !PublicationStatus.of(series.getRecordJson()).isPublished()) {
            return Optional.empty();
        }
        if (episode.getSeasonId() != null && !episode.getSeasonId().isEmpty()) {
            if (season == null || !PublicationStatus.of(season.getRecordJson()).isPublished()) {
                return Optional.empty();
            }
        }
        boolean authenticated = viewer != null;
        boolean admin = authenticated && viewer.isAdmin();
        if (catalogAsset == null || !catalogAsset.isAccessibleBy(authenticated, admin)) {
            return Optional.empty();
        }
        if (!catalogAsset.isEpisodeVideo()) {
            return Optional.empty();
        }
        if (!catalogAsset.coversFullEpisode(episodeId)) {
            return Optional.empty();
        }
        if (mediaAsset == null || !mediaAsset.isAvailable() || !lowercase(mediaAsset.getMimeType()).startsWith("video/")) {
            return Optional.empty();
        }
        return Optional.of(new PlaybackContext(episode, series, season, catalogAsset, mediaAsset));
    }

    private static String lowercase(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
