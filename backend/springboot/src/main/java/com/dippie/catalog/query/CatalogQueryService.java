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
import com.dippie.catalog.domain.MediaFileAvailability;
import com.dippie.catalog.domain.PublicationStatus;
import com.dippie.common.error.NotFoundException;
import com.dippie.common.rating.RatingLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Facade gom các repo JPA, bộ đệm đọc và điểm đánh giá lại một chỗ để controller không phải gọi rời rạc nhiều tầng.
@Service
class CatalogQueryService {

    private static final int DEFAULT_SERIES_EPISODE_LIMIT = 500;
    private static final int MAX_SERIES_EPISODE_LIMIT = 500;

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
    public SeriesDetail seriesDetail(String id, boolean admin) {
        return seriesDetail(id, admin, null, null);
    }

    // Tải chi tiết bộ phim cùng các mùa và một lát cắt tập phim; mình chặn tối đa 500 tập mỗi lần để mấy bộ dài hàng nghìn tập không kéo sập bộ nhớ hay treo phản hồi.
    @Transactional(readOnly = true)
    public SeriesDetail seriesDetail(String id, boolean admin, Integer episodeOffset, Integer episodeLimit) {
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
                ? DEFAULT_SERIES_EPISODE_LIMIT
                : Math.min(episodeLimit, MAX_SERIES_EPISODE_LIMIT);
        List<CatalogEpisode> visibleEpisodes = candidates.stream()
                .filter(episode -> admin || CatalogVisibility.followsPublishedParents(episode.getSeriesId(),
                        episode.getSeasonId(), foundSeries.getId()::equals, seasonParents::get))
                .skip(offset)
                .limit(limit)
                .toList();
        var seriesView = CatalogSeriesView.from(foundSeries).withRatings(ratings.forItem(id));
        return new SeriesDetail(seriesView, visibleSeasons.stream().map(CatalogSeasonView::from).toList(),
                ratings.withSummaries(visibleEpisodes, CatalogEpisode::getId, CatalogEpisodeView::from));
    }

    // Tải chi tiết tập phim sau khi kiểm tra chuỗi cha đã xuất bản, đối chiếu tệp media thực tế trên đĩa và phân giải nguồn YouTube.
    @Transactional(readOnly = true)
    public EpisodeDetail episodeDetail(String id, boolean authenticated, boolean admin) {
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
        return new EpisodeDetail(CatalogEpisodeView.from(episode, ratings.forItem(id)), visibleSegments, visibleAssets,
                visibleScenes, youtubeDetail.primary(), youtubeDetail.fallback());
    }

    // Kết quả chi tiết một bộ phim kèm danh sách mùa và lát cắt tập phim hợp lệ.
    record SeriesDetail(CatalogSeriesView series, List<CatalogSeasonView> seasons,
            List<CatalogEpisodeView> episodes) {

        SeriesDetail {
            seasons = seasons == null ? List.of() : List.copyOf(seasons);
            episodes = episodes == null ? List.of() : List.copyOf(episodes);
        }
    }

    // Kết quả chi tiết một tập phim kèm phân đoạn, tệp phát, phân cảnh và nguồn YouTube chính lẫn dự phòng.
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
