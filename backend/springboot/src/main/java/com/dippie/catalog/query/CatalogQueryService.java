package com.dippie.catalog.query;

import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogEpisodeRepository;
import com.dippie.catalog.domain.CatalogEpisodeView;
import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.catalog.domain.CatalogMediaAssetRepository;
import com.dippie.catalog.domain.CatalogMediaAssetView;
import com.dippie.catalog.domain.CatalogRecord;
import com.dippie.catalog.domain.CatalogSceneRepository;
import com.dippie.catalog.domain.CatalogSceneView;
import com.dippie.catalog.domain.CatalogSeason;
import com.dippie.catalog.domain.CatalogSeasonRepository;
import com.dippie.catalog.domain.CatalogSeasonView;
import com.dippie.catalog.domain.CatalogSegmentRepository;
import com.dippie.catalog.domain.CatalogSegmentView;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.domain.CatalogSeriesRepository;
import com.dippie.catalog.domain.CatalogSeriesView;
import com.dippie.common.error.NotFoundException;
import com.dippie.media.MediaAsset;
import com.dippie.media.MediaAssetRepository;
import com.dippie.media.MediaFileResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
class CatalogQueryService {

    private final CatalogSeriesRepository series;
    private final CatalogSeasonRepository seasons;
    private final CatalogEpisodeRepository episodes;
    private final CatalogSegmentRepository segments;
    private final CatalogMediaAssetRepository assets;
    private final MediaAssetRepository mediaAssets;
    private final MediaFileResolver mediaFileResolver;
    private final CatalogSceneRepository scenes;
    private final CatalogVisibility visibility;
    private final CatalogReadModelCache cache;

    public CatalogQueryService(CatalogSeriesRepository series, CatalogSeasonRepository seasons,
            CatalogEpisodeRepository episodes, CatalogSegmentRepository segments, CatalogMediaAssetRepository assets,
            CatalogSceneRepository scenes, CatalogVisibility visibility, CatalogReadModelCache cache,
            MediaAssetRepository mediaAssets, MediaFileResolver mediaFileResolver) {
        this.series = series;
        this.seasons = seasons;
        this.episodes = episodes;
        this.segments = segments;
        this.assets = assets;
        this.mediaAssets = mediaAssets;
        this.mediaFileResolver = mediaFileResolver;
        this.scenes = scenes;
        this.visibility = visibility;
        this.cache = cache;
    }

    public List<CatalogSeriesView> listSeries(boolean admin) {
        return cache.current().series(admin);
    }

    public List<SeriesCard> listSeriesCards() {
        return cache.current().seriesCards();
    }

    @Transactional(readOnly = true)
    public SeriesDetail seriesDetail(String id, boolean admin) {
        CatalogSeries row = series.findById(id).filter(candidate -> admin || candidate.isPublished())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bộ phim (series) với mã: " + id));
        List<CatalogSeason> visibleSeasons = seasons.findBySeriesIdOrderByNumberAsc(id).stream()
                .filter(season -> admin || season.isPublished()).toList();
        Map<String, String> seasonParents = visibleSeasons.stream()
                .collect(Collectors.toMap(CatalogRecord::getId, CatalogSeason::getSeriesId));
        List<CatalogEpisode> candidates = admin ? episodes.findBySeriesIdOrderByNumberAsc(id)
                : episodes.findBySeriesIdAndPublicationStatusOrderByNumberAsc(id, "ready");
        List<CatalogEpisodeView> visibleEpisodes = candidates.stream()
                .filter(episode -> admin || CatalogVisibility.followsPublishedParents(episode.getSeriesId(),
                        episode.getSeasonId(), row.getId()::equals, seasonParents::get))
                .map(CatalogEpisodeView::from).toList();
        return new SeriesDetail(CatalogSeriesView.from(row), visibleSeasons.stream().map(CatalogSeasonView::from).toList(),
                visibleEpisodes);
    }

    @Transactional(readOnly = true)
    public EpisodeDetail episodeDetail(String id, boolean authenticated, boolean admin) {
        CatalogEpisode episode = episodes.findById(id).filter(row -> admin || visibility.isEpisodeVisible(row))
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tập phim với mã: " + id));
        List<CatalogSegmentView> visibleSegments = segments.findByEpisodeIdOrderByPositionAsc(id).stream()
                .filter(segment -> admin || segment.isPublished()).map(CatalogSegmentView::from).toList();
        List<CatalogMediaAsset> accessibleAssets = assets.findByEpisodeId(id).stream()
                .filter(asset -> asset.isAccessibleBy(authenticated, admin)).toList();
        Set<String> assetIds = accessibleAssets.stream().map(CatalogMediaAsset::getId).collect(Collectors.toSet());
        Map<String, MediaAsset> fileAssets = mediaAssets.findAllById(assetIds).stream()
                .collect(Collectors.toMap(MediaAsset::getId, asset -> asset));
        List<CatalogMediaAssetView> visibleAssets = accessibleAssets.stream()
                .map(asset -> CatalogMediaAssetView.from(asset, Optional.ofNullable(fileAssets.get(asset.getId()))
                        .flatMap(mediaFileResolver::resolve).isPresent())).toList();
        List<CatalogSceneView> visibleScenes = scenes.findByEpisodeIdOrderByStartMsAsc(id).stream()
                .filter(scene -> scene.isVisibleTo(admin, assetIds)).map(CatalogSceneView::from).toList();
        YoutubeSource youtube = YoutubeSource.from(episode.getRawJson());
        YoutubeSource youtubeFallback = youtube == null ? YoutubeSource.fallbackFrom(episode.getRawJson()) : null;
        return new EpisodeDetail(CatalogEpisodeView.from(episode), visibleSegments, visibleAssets, visibleScenes,
                youtube, youtubeFallback);
    }

    record SeriesDetail(CatalogSeriesView series, List<CatalogSeasonView> seasons,
            List<CatalogEpisodeView> episodes) {

        SeriesDetail {
            seasons = seasons == null ? List.of() : List.copyOf(seasons);
            episodes = episodes == null ? List.of() : List.copyOf(episodes);
        }
    }

    record EpisodeDetail(CatalogEpisodeView episode, List<CatalogSegmentView> segments,
            List<CatalogMediaAssetView> mediaAssets, List<CatalogSceneView> scenes, YoutubeSource youtube,
            YoutubeSource youtubeFallback) {

        EpisodeDetail {
            segments = segments == null ? List.of() : List.copyOf(segments);
            mediaAssets = mediaAssets == null ? List.of() : List.copyOf(mediaAssets);
            scenes = scenes == null ? List.of() : List.copyOf(scenes);
        }
    }
}
