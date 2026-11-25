package com.dippie.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// Repository truy vấn tập phim trong catalog, kèm sẵn mấy câu join phim bộ và mùa cha để bên ngoài khỏi bắn thêm query lẻ.
public interface CatalogEpisodeRepository extends JpaRepository<CatalogEpisode, String> {

    List<CatalogEpisode> findBySeriesIdOrderByNumberAsc(String seriesId);

    List<CatalogEpisode> findBySeriesIdAndPublicationStatusOrderByNumberAsc(String seriesId, String publicationStatus);

    // Chỉ lấy những tập đã ready và có bản ghi tương ứng bên bảng Movie kế thừa, kèm luôn series với season cha.
    @Query("""
            select new com.dippie.catalog.domain.CatalogEpisodeRepository$EpisodeWithParents(episode, series, season)
            from CatalogEpisode episode
            join Movie movie on movie.id = episode.id
            join CatalogSeries series on series.id = episode.seriesId
            left join CatalogSeason season on season.id = episode.seasonId and season.seriesId = series.id
            where episode.id in :ids and episode.publicationStatus = 'ready'
            """)
    List<EpisodeWithParents> findReadyWithParentsAndMovieByIdIn(@Param("ids") List<String> ids);

    // Lần ngược từ một media asset ra các tập gắn với nó kèm series và season cha để kiểm tra điều kiện xuất bản.
    @Query("""
            select new com.dippie.catalog.domain.CatalogEpisodeRepository$EpisodeWithParents(episode, series, season)
            from CatalogEpisodeAsset link
            join CatalogEpisode episode on episode.id = link.episodeId
            join CatalogSeries series on series.id = episode.seriesId
            left join CatalogSeason season on season.id = episode.seasonId and season.seriesId = series.id
            where link.mediaAssetId = :assetId
            """)
    List<EpisodeWithParents> findWithParentsByMediaAssetId(@Param("assetId") String assetId);

    // Bản truy vấn theo lô cho nhiều media asset cùng lúc, trả về mảng cột gồm assetId và bộ ba episode, series, season.
    @Query("""
            select link.mediaAssetId, episode, series, season
            from CatalogEpisodeAsset link
            join CatalogEpisode episode on episode.id = link.episodeId
            join CatalogSeries series on series.id = episode.seriesId
            left join CatalogSeason season on season.id = episode.seasonId and season.seriesId = series.id
            where link.mediaAssetId in :assetIds
            """)
    List<Object[]> findWithParentsByMediaAssetIdIn(@Param("assetIds") List<String> assetIds);

    // Record gộp tập phim với series và season cha (season có thể null nếu tập không thuộc mùa nào).
    record EpisodeWithParents(CatalogEpisode episode, CatalogSeries series, CatalogSeason season) {
        // Tập chỉ coi là công khai khi cả tập lẫn phim bộ đều ready; nếu có seasonId thì mùa đó cũng phải tồn tại và đã ready.
        public boolean isPublished() {
            String seasonId = episode.getSeasonId();
            return episode.isPublished()
                    && series.isPublished()
                    && (seasonId == null || seasonId.isEmpty()
                    || (season != null && season.isPublished()));
        }
    }
}
