package com.dippie.catalog.query;

import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogEpisodeRepository;
import com.dippie.catalog.domain.CatalogGenreRepository;
import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.catalog.domain.CatalogMediaAssetRepository;
import com.dippie.catalog.domain.CatalogRecord;
import com.dippie.catalog.domain.CatalogSceneRepository;
import com.dippie.catalog.domain.CatalogSeason;
import com.dippie.catalog.domain.CatalogSeasonRepository;
import com.dippie.catalog.domain.CatalogSegmentRepository;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.catalog.domain.CatalogSeriesRepository;
import com.dippie.catalog.domain.CatalogSeriesView;
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

@Component
class CatalogReadModelLoader {

    private final CatalogSeriesRepository series;
    private final CatalogSeasonRepository seasons;
    private final CatalogEpisodeRepository episodes;
    private final CatalogGenreRepository genres;
    private final CatalogSegmentRepository segments;
    private final CatalogSceneRepository scenes;
    private final CatalogMediaAssetRepository assets;
    private final EntityManager entities;

    CatalogReadModelLoader(CatalogSeriesRepository series, CatalogSeasonRepository seasons,
            CatalogEpisodeRepository episodes, CatalogGenreRepository genres, CatalogSegmentRepository segments,
            CatalogSceneRepository scenes, CatalogMediaAssetRepository assets, EntityManager entities) {
        this.series = series;
        this.seasons = seasons;
        this.episodes = episodes;
        this.genres = genres;
        this.segments = segments;
        this.scenes = scenes;
        this.assets = assets;
        this.entities = entities;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
    public CatalogReadModel load() {
        List<CatalogSeries> seriesRows = series.findAll();
        Map<String, CatalogSeries> seriesById = byId(seriesRows);
        List<CatalogSeason> seasonRows = seasons.findAll();
        Map<String, CatalogSeason> seasonsById = byId(seasonRows);
        Map<String, String> seasonParents = seasonRows.stream().filter(CatalogRecord::isPublished)
                .collect(Collectors.toMap(CatalogSeason::getId, CatalogSeason::getSeriesId));
        List<CatalogEpisode> episodeRows = episodes.findAll();
        Map<String, CatalogEpisode> episodesById = byId(episodeRows);
        AssetCoverage coverage = assetCoverage();

        List<SearchRow> search = new ArrayList<>();
        Set<String> visibleSeries = new HashSet<>();
        Set<String> visibleEpisodes = new HashSet<>();
        Set<String> searchableEpisodes = new HashSet<>();
        Set<String> hiddenMovies = new HashSet<>();
        Map<String, List<SeriesEpisode>> seriesEpisodes = new HashMap<>();
        Map<String, SeriesEpisode> visibleEpisodeRows = new HashMap<>();

        for (CatalogSeries row : seriesRows) {
            boolean visible = row.isPublished();
            (visible ? visibleSeries : hiddenMovies).add(row.getId());
            Hit hit = new Hit(row.getId(), "series", row.getTitle(), null, row.getId(), null, null,
                    null, null, row.getSynopsis());
            search.add(SearchRow.of(hit, visible, visible, row.getTitle(), row.getOriginalTitle(), row.getSynopsis(),
                    row.getKeywordsJson()));
        }
        for (CatalogEpisode row : episodeRows) {
            String id = row.getId();
            boolean visibleParents = CatalogVisibility.followsPublishedParents(row.getSeriesId(), row.getSeasonId(),
                    visibleSeries::contains, seasonParents::get);
            if (row.isPublished() && visibleParents) {
                visibleEpisodes.add(id);
                boolean memberAccessible = coverage.playable().getOrDefault(id, true);
                CatalogSeason season = seasonsById.get(row.getSeasonId());
                SeriesEpisode episode = new SeriesEpisode(id, row.getTitle(), season == null ? null : season.getNumber(),
                        row.getNumber(), row.getDurationMs(), memberAccessible, row.getSeriesId(),
                        season == null ? null : season.getId(), YoutubeSource.artworkFrom(row.getRawJson()));
                visibleEpisodeRows.put(id, episode);
                seriesEpisodes.computeIfAbsent(row.getSeriesId(), ignored -> new ArrayList<>())
                        .add(episode);
            } else {
                hiddenMovies.add(id);
            }
            CatalogSeries parent = seriesById.get(row.getSeriesId());
            if (parent != null) {
                boolean searchable = visibleParents && "ready".equals(row.getPublicationStatus());
                if (searchable) {
                    searchableEpisodes.add(id);
                }
                Hit hit = new Hit(id, "episode", row.getTitle(), parent.getTitle(), row.getSeriesId(), id, null,
                        0L, row.getDurationMs(), row.getSynopsis());
                search.add(SearchRow.of(hit, searchable, searchable, row.getTitle(), row.getSynopsis(),
                        row.getPlotFactsJson(), row.getKeywordsJson()));
            }
        }
        addChildSearchRows(search, seriesById, episodesById, searchableEpisodes, coverage);

        var candidates = new CatalogRecommendationRows(entities).load(episodeRows, seriesById, seasonsById,
                row -> visibleEpisodes.contains(row.getId()) && "ready".equals(row.getPublicationStatus())
                        && coverage.recommends(row), id -> visibleEpisodeRows.get(id).artwork());
        var genresById = genres.findAll().stream().map(SeriesCard.Genre::from)
                .collect(Collectors.toMap(SeriesCard.Genre::id, Function.identity()));
        var cards = seriesRows.stream().filter(row -> visibleSeries.contains(row.getId()))
                .map(row -> SeriesCard.from(row, seriesEpisodes.getOrDefault(row.getId(), List.of()), genresById))
                .sorted(Comparator.comparing(SeriesCard::title).thenComparing(SeriesCard::id)).toList();
        return new CatalogReadModel(seriesRows.stream().map(CatalogSeriesView::from).toList(), visibleSeries,
                visibleEpisodes, hiddenMovies, search, candidates, seriesEpisodes, cards, visibleEpisodeRows);
    }

    private void addChildSearchRows(List<SearchRow> search, Map<String, CatalogSeries> seriesById,
            Map<String, CatalogEpisode> episodesById, Set<String> searchableEpisodes, AssetCoverage coverage) {
        for (var segment : segments.findAll()) {
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
        List<CatalogMediaAsset> assetRows = assets.findAll();
        Set<String> mediaIds = assetRows.isEmpty() ? Set.of() : new HashSet<>(entities.createQuery("""
                select media.id from MediaAsset media join CatalogMediaAsset asset on media.id = asset.id
                where media.available = true and lower(media.mimeType) like 'video/%'
                """, String.class).getResultList());
        Set<String> publicAssets = new HashSet<>();
        Set<String> memberAssets = new HashSet<>();
        Set<String> declaredEpisodes = new HashSet<>();
        Set<String> recommendedEpisodes = new HashSet<>();
        Map<String, Boolean> playable = new HashMap<>();

        for (CatalogMediaAsset asset : assetRows) {
            String id = asset.getId();
            boolean publicAccess = asset.isAccessibleBy(false, false);
            boolean memberAccess = asset.isAccessibleBy(true, false);
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
            boolean playableVideo = episodeVideo && mediaIds.contains(id) && asset.isAccessibleBy(true, true);
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
            }
            if (playableVideo) {
                fullEpisodes.forEach(episodeId -> playable.merge(episodeId, memberAccess, Boolean::logicalOr));
            }
        }
        return new AssetCoverage(publicAssets, memberAssets, declaredEpisodes, recommendedEpisodes, playable);
    }

    private static <T extends CatalogRecord> Map<String, T> byId(List<T> rows) {
        return rows.stream().collect(Collectors.toMap(CatalogRecord::getId, Function.identity()));
    }

    private record AssetCoverage(Set<String> publicAssets, Set<String> memberAssets, Set<String> declaredEpisodes,
            Set<String> recommendedEpisodes, Map<String, Boolean> playable) {

        boolean recommends(CatalogEpisode episode) {
            return recommendedEpisodes.contains(episode.getId()) || (!declaredEpisodes.contains(episode.getId())
                    && JsonText.parse(episode.getRawJson())
                            .map(row -> row.path("primary_media_asset_id").asText("").isEmpty()).orElse(true));
        }
    }
}
