package com.dippie.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// Kho truy vấn tài nguyên media trong bảng catalog_media_assets.
public interface CatalogMediaAssetRepository extends JpaRepository<CatalogMediaAsset, String> {

    // Lấy toàn bộ media asset nối với một tập phim qua bảng catalog_episode_assets, sắp theo id.
    @Query("""
            select asset from CatalogMediaAsset asset
            where asset.id in (
                select link.mediaAssetId from CatalogEpisodeAsset link where link.episodeId = :episodeId
            )
            order by asset.id
            """)
    List<CatalogMediaAsset> findByEpisodeId(@Param("episodeId") String episodeId);
}
