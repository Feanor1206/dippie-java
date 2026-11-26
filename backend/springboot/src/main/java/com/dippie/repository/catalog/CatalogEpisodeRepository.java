package com.dippie.repository.catalog;

import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogSeason;
import com.dippie.model.catalog.CatalogSeries;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// repo tập phim trong catalog, viết sẵn mấy câu join bộ với mùa cha để bên ngoài khỏi bắn query lẻ
public interface CatalogEpisodeRepository extends JpaRepository<CatalogEpisode, String> {

    List<CatalogEpisode> findBySeriesIdOrderByNumberAsc(String seriesId);

    List<CatalogEpisode> findBySeriesIdAndPublicationStatusOrderByNumberAsc(String seriesId, String publicationStatus);

    // chỉ lấy tập đã ready có bản ghi khớp bên Movie, join sẵn series với season cha
    @Query("""
            select new com.dippie.repository.catalog.CatalogEpisodeRepository$EpisodeWithParents(episode, series, season)
            from CatalogEpisode episode
            join Movie movie on movie.id = episode.id
            join CatalogSeries series on series.id = episode.seriesId
            left join CatalogSeason season on season.id = episode.seasonId and season.seriesId = series.id
            where episode.id in :ids and episode.publicationStatus = 'ready'
            """)
    List<EpisodeWithParents> findReadyWithParentsAndMovieByIdIn(@Param("ids") List<String> ids);

    // lần ngược từ một media asset ra mấy tập dính tới nó kèm series, season cha để check xuất bản
    @Query("""
            select new com.dippie.repository.catalog.CatalogEpisodeRepository$EpisodeWithParents(episode, series, season)
            from CatalogEpisodeAsset link
            join CatalogEpisode episode on episode.id = link.episodeId
            join CatalogSeries series on series.id = episode.seriesId
            left join CatalogSeason season on season.id = episode.seasonId and season.seriesId = series.id
            where link.mediaAssetId = :assetId
            """)
    List<EpisodeWithParents> findWithParentsByMediaAssetId(@Param("assetId") String assetId);

    // bản query theo lô cho nhiều media asset một lượt, trả assetId kèm bộ ba episode, series, season
    @Query("""
            select link.mediaAssetId, episode, series, season
            from CatalogEpisodeAsset link
            join CatalogEpisode episode on episode.id = link.episodeId
            join CatalogSeries series on series.id = episode.seriesId
            left join CatalogSeason season on season.id = episode.seasonId and season.seriesId = series.id
            where link.mediaAssetId in :assetIds
            """)
    List<Object[]> findWithParentsByMediaAssetIdIn(@Param("assetIds") List<String> assetIds);

    // gom tập với series và season cha, season có thể null nếu tập không thuộc mùa nào
    record EpisodeWithParents(CatalogEpisode episode, CatalogSeries series, CatalogSeason season) {
        // chỉ coi là công khai khi cả tập lẫn bộ đều ready, có seasonId thì mùa đó cũng phải tồn tại và ready
        public boolean isPublished() {
            String seasonId = episode.getSeasonId();
            return episode.isPublished()
                    && series.isPublished()
                    && (seasonId == null || seasonId.isEmpty()
                    || (season != null && season.isPublished()));
        }
    }
}
