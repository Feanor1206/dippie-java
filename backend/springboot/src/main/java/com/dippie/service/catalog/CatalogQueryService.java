package com.dippie.service.catalog;

import com.dippie.common.rating.RatingLookup;
import com.dippie.dto.catalog.CatalogEpisodeDetail;
import com.dippie.dto.catalog.CatalogEpisodeView;
import com.dippie.dto.catalog.CatalogMediaAssetView;
import com.dippie.dto.catalog.CatalogSceneView;
import com.dippie.dto.catalog.CatalogSeasonView;
import com.dippie.dto.catalog.CatalogSegmentView;
import com.dippie.dto.catalog.CatalogSeriesDetail;
import com.dippie.dto.catalog.CatalogSeriesView;
import com.dippie.dto.catalog.SeriesCard;
import com.dippie.exception.NotFoundException;
import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogMediaAsset;
import com.dippie.model.catalog.CatalogRecord;
import com.dippie.model.catalog.CatalogSeason;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.catalog.PublicationStatus;
import com.dippie.model.catalog.YoutubeSource;
import com.dippie.repository.catalog.CatalogEpisodeRepository;
import com.dippie.repository.catalog.CatalogMediaAssetRepository;
import com.dippie.repository.catalog.CatalogSceneRepository;
import com.dippie.repository.catalog.CatalogSeasonRepository;
import com.dippie.repository.catalog.CatalogSegmentRepository;
import com.dippie.repository.catalog.CatalogSeriesRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// kiểu Facade gom hết repo JPA, cache đọc với điểm đánh giá về 1 chỗ cho controller khỏi gọi lắt nhắt nhiều tầng
@Service
public class CatalogQueryService {

    private final CatalogSeriesRepository series;
    private final CatalogSeasonRepository seasons;
    private final CatalogEpisodeRepository episodes;
    private final CatalogSegmentRepository segments;
    private final CatalogMediaAssetRepository assets;
    private final MediaFileAvailability mediaAvailability;
    private final CatalogSceneRepository scenes;
    private final CatalogVisibility visibility;
    private final CatalogReadModelCache cache;
    private final RatingLookup ratings;

    public CatalogQueryService(CatalogSeriesRepository series, CatalogSeasonRepository seasons,
            CatalogEpisodeRepository episodes, CatalogSegmentRepository segments, CatalogMediaAssetRepository assets,
            CatalogSceneRepository scenes, CatalogVisibility visibility, CatalogReadModelCache cache,
            MediaFileAvailability mediaAvailability, RatingLookup ratings) {
        this.series = series;
        this.seasons = seasons;
        this.episodes = episodes;
        this.segments = segments;
        this.assets = assets;
        this.mediaAvailability = mediaAvailability;
        this.scenes = scenes;
        this.visibility = visibility;
        this.cache = cache;
        this.ratings = ratings;
    }

    @Transactional(readOnly = true)
    public List<CatalogSeriesView> listSeries(boolean admin) {
        return ratings.withSummaries(cache.current().series(admin), CatalogSeriesView::id, CatalogSeriesView::withRatings);
    }

    @Transactional(readOnly = true)
    public List<SeriesCard> listSeriesCards() {
        return ratings.withSummaries(cache.current().seriesCards(), SeriesCard::id, SeriesCard::withRatings);
    }

    @Transactional(readOnly = true)
    public CatalogSeriesDetail seriesDetail(String id, boolean admin) {
        return seriesDetail(id, admin, null, null);
    }

    // tải chi tiết bộ phim kèm mùa và một lát cắt tập, chặn tối đa 500 tập mỗi lần kẻo mấy bộ vài nghìn tập kéo sập RAM hay treo response
    @Transactional(readOnly = true)
    public CatalogSeriesDetail seriesDetail(String id, boolean admin, Integer episodeOffset, Integer episodeLimit) {
        CatalogSeries foundSeries = series.findById(id).filter(candidate -> admin || candidate.isPublished())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bộ phim với mã: " + id));
        List<CatalogSeason> visibleSeasons = seasons.findBySeriesIdOrderByNumberAsc(id).stream()
                .filter(season -> admin || season.isPublished()).toList();
        Map<String, String> seasonParents = visibleSeasons.stream()
                .collect(Collectors.toMap(CatalogRecord::getId, CatalogSeason::getSeriesId));
        List<CatalogEpisode> candidates = admin ? episodes.findBySeriesIdOrderByNumberAsc(id)
                : episodes.findBySeriesIdAndPublicationStatusOrderByNumberAsc(id, PublicationStatus.READY_VALUE);
        int offset = episodeOffset == null || episodeOffset < 0 ? 0 : episodeOffset;
        int limit = (episodeLimit == null || episodeLimit <= 0)
                ? 500
                : Math.min(episodeLimit, 500);
        List<CatalogEpisode> visibleEpisodes = candidates.stream()
                .filter(episode -> admin || CatalogVisibility.followsPublishedParents(episode.getSeriesId(),
                        episode.getSeasonId(), foundSeries.getId()::equals, seasonParents::get))
                .skip(offset)
                .limit(limit)
                .toList();
        var seriesView = CatalogSeriesView.from(foundSeries).withRatings(ratings.forItem(id));
        return new CatalogSeriesDetail(seriesView, visibleSeasons.stream().map(CatalogSeasonView::from).toList(),
                ratings.withSummaries(visibleEpisodes, CatalogEpisode::getId, CatalogEpisodeView::from));
    }

    // tải chi tiết tập phim sau khi check chuỗi cha đã xuất bản, dò file media thật ngoài đĩa rồi giải nguồn YouTube
    @Transactional(readOnly = true)
    public CatalogEpisodeDetail episodeDetail(String id, boolean authenticated, boolean admin) {
        CatalogEpisode episode = episodes.findById(id).filter(candidate -> admin || visibility.isEpisodeVisible(candidate))
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tập phim với mã: " + id));
        List<CatalogSegmentView> visibleSegments = segments.findByEpisodeIdOrderByPositionAsc(id).stream()
                .filter(segment -> admin || segment.isPublished()).map(CatalogSegmentView::from).toList();
        List<CatalogMediaAsset> accessibleAssets = assets.findByEpisodeId(id).stream()
                .filter(asset -> asset.isAccessibleBy(authenticated, admin)).toList();
        Set<String> assetIds = accessibleAssets.stream().map(CatalogMediaAsset::getId).collect(Collectors.toSet());
        Set<String> availableFiles = mediaAvailability.availableAssetIds(assetIds);
        List<CatalogMediaAssetView> visibleAssets = accessibleAssets.stream()
                .map(asset -> CatalogMediaAssetView.from(asset, availableFiles.contains(asset.getId()))).toList();
        List<CatalogSceneView> visibleScenes = scenes.findByEpisodeIdOrderByStartMsAsc(id).stream()
                .filter(scene -> scene.isVisibleTo(admin, assetIds)).map(CatalogSceneView::from).toList();
        YoutubeSource.Detail youtubeDetail = YoutubeSource.resolveDetail(episode.getRawJson());
        return new CatalogEpisodeDetail(CatalogEpisodeView.from(episode, ratings.forItem(id)), visibleSegments, visibleAssets,
                visibleScenes, youtubeDetail.primary(), youtubeDetail.fallback());
    }


}
