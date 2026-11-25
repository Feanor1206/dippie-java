package com.dippie.catalog.query;

import com.dippie.catalog.domain.AccessPolicy;
import com.dippie.catalog.domain.CatalogCharacter;
import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogEpisodeRepository;
import com.dippie.catalog.domain.CatalogGenre;
import com.dippie.catalog.domain.CatalogGenreRepository;
import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.catalog.domain.CatalogMediaAssetRepository;
import com.dippie.catalog.domain.CatalogRecord;
import com.dippie.catalog.domain.CatalogSceneRepository;
import com.dippie.catalog.domain.CatalogSeason;
import com.dippie.catalog.domain.CatalogSeasonRepository;
import com.dippie.catalog.domain.CatalogSegment;
import com.dippie.catalog.domain.CatalogSegmentRepository;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.domain.CatalogSeriesRepository;
import com.dippie.catalog.domain.CatalogSeriesView;
import com.dippie.catalog.domain.EpisodeRuntime;
import com.dippie.catalog.domain.MediaFileAvailability;
import com.dippie.catalog.domain.MoviePresentationLookup;
import com.dippie.catalog.domain.PublicationStatus;
import com.dippie.catalog.query.CatalogReadModel.GenreEntry;
import com.dippie.catalog.query.CatalogReadModel.SeriesEpisode;
import com.dippie.catalog.query.CatalogSearchService.Hit;
import com.dippie.catalog.query.CatalogSearchService.SearchRow;
import com.dippie.common.json.JsonText;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.dippie.catalog.query.CatalogSearchService.firstText;

// Gom toàn bộ dữ liệu danh mục một lần từ CSDL trong giao dịch chỉ đọc REPEATABLE_READ + REQUIRES_NEW để dựng CatalogReadModel.
// Chạy tách hẳn giao dịch mới ở đây giúp bản chụp luôn thấy cùng một mốc dữ liệu khi import đang diễn ra và tránh đổ tải join nặng vào từng request người dùng.
@Component
class CatalogReadModelLoader {

    private final CatalogSeriesRepository series;
    private final CatalogSeasonRepository seasons;
    private final CatalogEpisodeRepository episodes;
    private final CatalogGenreRepository genres;
    private final CatalogSegmentRepository segments;
    private final CatalogSceneRepository scenes;
    private final CatalogMediaAssetRepository assets;
    private final MediaFileAvailability mediaAvailability;
    private final MoviePresentationLookup moviePresentations;
    private final EntityManager entities;

    CatalogReadModelLoader(CatalogSeriesRepository series, CatalogSeasonRepository seasons,
            CatalogEpisodeRepository episodes, CatalogGenreRepository genres, CatalogSegmentRepository segments,
            CatalogSceneRepository scenes, CatalogMediaAssetRepository assets, MediaFileAvailability mediaAvailability,
            MoviePresentationLookup moviePresentations, EntityManager entities) {
        this.series = series;
        this.seasons = seasons;
        this.episodes = episodes;
        this.genres = genres;
        this.segments = segments;
        this.scenes = scenes;
        this.assets = assets;
        this.mediaAvailability = mediaAvailability;
        this.moviePresentations = moviePresentations;
        this.entities = entities;
    }

    // Tải các bảng danh mục, tính quyền hiển thị theo chuỗi cha rồi dựng đủ dữ liệu cho tìm kiếm, thẻ phim và gợi ý.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
    public CatalogReadModel load() {
        // Đọc một lượt các bảng chính và lập map tra cứu theo mã.
        List<CatalogSeries> seriesRows = series.findAll();
        Map<String, CatalogSeries> seriesById = byId(seriesRows);
        List<CatalogSeason> seasonRows = seasons.findAll();
        Map<String, CatalogSeason> seasonsById = byId(seasonRows);
        Map<String, String> seasonParents = seasonRows.stream().filter(CatalogRecord::isPublished)
                .collect(Collectors.toMap(CatalogSeason::getId, CatalogSeason::getSeriesId));
        List<CatalogEpisode> episodeRows = episodes.findAll();
        Map<String, CatalogEpisode> episodesById = byId(episodeRows);
        AssetCoverage coverage = assetCoverage();
        var names = new CatalogSearchNames(entities.createQuery("from CatalogCharacter", CatalogCharacter.class)
                .getResultList());

        List<SearchRow> search = new ArrayList<>();
        Set<String> visibleSeries = new HashSet<>();
        Set<String> visibleEpisodes = new HashSet<>();
        Set<String> searchableEpisodes = new HashSet<>();
        Set<String> hiddenMovies = new HashSet<>();
        Map<String, List<SeriesEpisode>> seriesEpisodes = new HashMap<>();
        Map<String, SeriesEpisode> visibleEpisodeRows = new HashMap<>();
        Map<String, List<String>> externalEpisodeAssetIds = new HashMap<>();
        Map<String, YoutubeSource.EpisodeMetadata> episodeMetadata = new HashMap<>(episodeRows.size());

        // Tách bộ phim công khai với bộ phim ẩn và đẩy vào danh sách tìm kiếm.
        for (CatalogSeries row : seriesRows) {
            boolean visible = row.isPublished();
            (visible ? visibleSeries : hiddenMovies).add(row.getId());
            Hit hit = new Hit(row.getId(), "series", row.getTitle(), null, row.getId(), null, null,
                    null, null, row.getSynopsis());
            search.add(SearchRow.named(hit, visible, visible,
                    CatalogSearchNames.series(row),
                    names.characters(row.getCharacterIdsJson()), List.of(), row.getSynopsis(), row.getKeywordsJson()));
        }

        // Lọc quyền xem từng tập dựa trên trạng thái ready và chuỗi bộ/mùa cha, đồng thời tách riêng các tập bị khóa nhúng YouTube.
        for (CatalogEpisode row : episodeRows) {
            String id = row.getId();
            boolean visibleParents = CatalogVisibility.followsPublishedParents(row.getSeriesId(), row.getSeasonId(),
                    visibleSeries::contains, seasonParents::get);
            YoutubeSource.EpisodeMetadata metadata = YoutubeSource.parseEpisodeMetadata(row.getRawJson());
            episodeMetadata.put(id, metadata);
            boolean ready = PublicationStatus.READY_VALUE.equals(row.getPublicationStatus());
            if (ready && visibleParents) {
                visibleEpisodes.add(id);
                boolean memberAccessible = coverage.playable().getOrDefault(id, true);
                CatalogSeason season = seasonsById.get(row.getSeasonId());
                SeriesEpisode episode = new SeriesEpisode(id, row.getTitle(), season == null ? null : season.getNumber(),
                        row.getNumber(), EpisodeRuntime.known(row.getDurationMs()), memberAccessible, row.getSeriesId(),
                        season == null ? null : season.getId(), metadata.artwork());
                visibleEpisodeRows.put(id, episode);
                seriesEpisodes.computeIfAbsent(row.getSeriesId(), ignored -> new ArrayList<>())
                        .add(episode);
            } else {
                hiddenMovies.add(id);
            }
            CatalogSeries parent = seriesById.get(row.getSeriesId());
            if (parent != null) {
                boolean searchable = visibleParents && ready;
                if (searchable) {
                    searchableEpisodes.add(id);
                    if (metadata.embeddingDisabled()) {
                        externalEpisodeAssetIds.put(id, List.copyOf(coverage.publicFileIds().getOrDefault(id, List.of())));
                    }
                }
                Hit hit = new Hit(id, "episode", row.getTitle(), parent.getTitle(), row.getSeriesId(), id, null,
                        0L, EpisodeRuntime.known(row.getDurationMs()), row.getSynopsis());
                search.add(SearchRow.named(hit, searchable, searchable,
                        CatalogSearchNames.of(row.getTitle(), metadata.originalTitle(), row.getAliasesJson()),
                        names.characters(row.getCharacterIdsJson()), CatalogSearchNames.aliases(row.getAliasesJson()),
                        row.getSynopsis(), row.getPlotFactsJson(),
                        row.getKeywordsJson(), row.getThemesJson()));
            }
        }

        // Nạp tiếp các phân đoạn và phân cảnh con gắn với tập phim hợp lệ.
        var segmentRows = segments.findAll();
        addChildSearchRows(search, seriesById, episodesById, searchableEpisodes, coverage, segmentRows);
        Set<String> episodesWithSegments = segmentRows.stream().filter(CatalogRecord::isPublished)
                .map(CatalogSegment::getEpisodeId).collect(Collectors.toSet());

        // Ghép hồ sơ gợi ý, danh sách thể loại và thẻ tóm tắt cho từng bộ phim công khai.
        var profiles = new CatalogRecommendationRows(entities, moviePresentations).load(episodeRows, seriesById,
                seasonsById, row -> visibleEpisodes.contains(row.getId())
                        && coverage.recommends(row, episodeMetadata.getOrDefault(row.getId(), YoutubeSource.EpisodeMetadata.EMPTY)),
                id -> visibleEpisodeRows.get(id).artwork(), episodesWithSegments);
        List<CatalogGenre> allGenres = genres.findAll();
        List<GenreEntry> genreEntries = allGenres.stream()
                .map(genre -> new GenreEntry(genre.getId(), genre.label("vi"), genre.label("en")))
                .toList();
        var genresById = allGenres.stream().map(SeriesCard.Genre::from)
                .collect(Collectors.toMap(SeriesCard.Genre::id, Function.identity()));
        var cards = seriesRows.stream().filter(row -> visibleSeries.contains(row.getId()))
                .map(row -> SeriesCard.from(row, seriesEpisodes.getOrDefault(row.getId(), List.of()), genresById))
                .sorted(Comparator.comparing(SeriesCard::title).thenComparing(SeriesCard::id)).toList();
        return new CatalogReadModel(seriesRows.stream().map(CatalogSeriesView::from).toList(), visibleSeries,
                visibleEpisodes, hiddenMovies, search, profiles, seriesEpisodes, cards, visibleEpisodeRows,
                externalEpisodeAssetIds, genreEntries);
    }

    private void addChildSearchRows(List<SearchRow> search, Map<String, CatalogSeries> seriesById,
            Map<String, CatalogEpisode> episodesById, Set<String> searchableEpisodes, AssetCoverage coverage,
            List<CatalogSegment> segmentRows) {
        // Đưa phân đoạn đã xuất bản vào danh sách tìm kiếm.
        for (var segment : segmentRows) {
            CatalogEpisode episode = episodesById.get(segment.getEpisodeId());
            if (episode != null && seriesById.containsKey(episode.getSeriesId())) {
                boolean searchable = searchableEpisodes.contains(episode.getId()) && segment.isPublished();
                Hit hit = new Hit(segment.getId(), "segment", segment.getTitle(), episode.getTitle(),
                        episode.getSeriesId(), episode.getId(), null, segment.getEpisodeStartMs(),
                        segment.getEpisodeEndMs(), segment.getSynopsis());
                search.add(SearchRow.of(hit, searchable, searchable, segment.getTitle(), segment.getSynopsis(),
                        segment.getPlotFactsJson(), segment.getKeywordsJson()));
            }
        }
        // Với phân cảnh, quyền xem còn phụ thuộc tệp media gắn với cảnh có mở cho khách hoặc thành viên hay không.
        for (var scene : scenes.findAll()) {
            CatalogEpisode episode = episodesById.get(scene.getEpisodeId());
            if (episode != null && seriesById.containsKey(episode.getSeriesId())) {
                boolean parentSearchable = searchableEpisodes.contains(episode.getId());
                Hit hit = new Hit(scene.getId(), "scene", firstText(scene.getTitle(), "Phân cảnh khớp truy vấn"),
                        episode.getTitle(), episode.getSeriesId(), episode.getId(), scene.getMediaAssetId(),
                        scene.getStartMs(), scene.getEndMs(), firstText(scene.getSummary(), scene.getVisualDescription()));
                search.add(SearchRow.of(hit, parentSearchable && scene.isVisibleTo(false, coverage.publicAssets()),
                        parentSearchable && scene.isVisibleTo(false, coverage.memberAssets()),
                        scene.getTitle(), scene.getSummary(), scene.getVisualDescription(), scene.getVisualTagsJson()));
            }
        }
    }

    private AssetCoverage assetCoverage() {
        // Đối chiếu danh sách media_assets trong DB với tệp video thực sự tồn tại trên ổ đĩa.
        List<CatalogMediaAsset> assetRows = assets.findAll();
        Map<String, Boolean> availableVideoPublic = assetRows.isEmpty()
                ? Map.of() : mediaAvailability.availableVideoAssets();
        Set<String> publicAssets = new HashSet<>();
        Set<String> memberAssets = new HashSet<>();
        Set<String> declaredEpisodes = new HashSet<>();
        Set<String> recommendedEpisodes = new HashSet<>();
        Map<String, Boolean> playable = new HashMap<>();
        Map<String, List<String>> publicFileIds = new HashMap<>();

        // Chia nhóm quyền công khai/thành viên và gom những tập có tệp phủ trọn tập (full_episode).
        for (CatalogMediaAsset asset : assetRows) {
            String id = asset.getId();
            boolean enabled = asset.isEnabled();
            boolean published = enabled && PublicationStatus.of(asset.getRecordJson()).isPublished();
            AccessPolicy policy = enabled ? AccessPolicy.of(asset.getAccessPolicy()) : null;
            boolean publicAccess = published && policy.allows(false, false);
            boolean memberAccess = published && policy.allows(true, false);
            boolean adminAccess = enabled && policy.allows(true, true);
            if (publicAccess) {
                publicAssets.add(id);
            }
            if (memberAccess) {
                memberAssets.add(id);
            }
            var entries = JsonText.parse(asset.getCoverageJson()).filter(JsonNode::isArray);
            if (entries.isEmpty()) {
                continue;
            }
            boolean episodeVideo = asset.isEpisodeVideo();
            Boolean mediaPublic = availableVideoPublic.get(id);
            boolean playableVideo = episodeVideo && mediaPublic != null && adminAccess;
            Set<String> fullEpisodes = new HashSet<>();
            for (JsonNode entry : entries.orElseThrow()) {
                String episodeId = entry.path("episode_id").textValue();
                if (episodeId != null) {
                    declaredEpisodes.add(episodeId);
                    if ("full_episode".equalsIgnoreCase(entry.path("scope").asText())) {
                        fullEpisodes.add(episodeId);
                    }
                }
            }
            if (episodeVideo && publicAccess) {
                recommendedEpisodes.addAll(fullEpisodes);
                if (Boolean.TRUE.equals(mediaPublic)) {
                    fullEpisodes.forEach(episodeId -> publicFileIds.computeIfAbsent(episodeId, key -> new ArrayList<>())
                            .add(id));
                }
            }
            if (playableVideo) {
                fullEpisodes.forEach(episodeId -> playable.merge(episodeId, memberAccess, Boolean::logicalOr));
            }
        }
        return new AssetCoverage(publicAssets, memberAssets, declaredEpisodes, recommendedEpisodes, playable, publicFileIds);
    }

    private static <T extends CatalogRecord> Map<String, T> byId(List<T> rows) {
        return rows.stream().collect(Collectors.toMap(CatalogRecord::getId, Function.identity()));
    }

    // Tổng hợp quyền truy cập media và danh sách tệp video công khai theo từng tập.
    private record AssetCoverage(Set<String> publicAssets, Set<String> memberAssets, Set<String> declaredEpisodes,
            Set<String> recommendedEpisodes, Map<String, Boolean> playable, Map<String, List<String>> publicFileIds) {

        // Tập được đưa vào gợi ý khi có tệp video công khai đủ tập, hoặc là tập không khai báo media nội bộ nào (ví dụ chỉ phát qua YouTube).
        boolean recommends(CatalogEpisode episode, YoutubeSource.EpisodeMetadata metadata) {
            return recommendedEpisodes.contains(episode.getId())
                    || (!declaredEpisodes.contains(episode.getId()) && !metadata.hasPrimaryMediaAsset());
        }
    }
}
