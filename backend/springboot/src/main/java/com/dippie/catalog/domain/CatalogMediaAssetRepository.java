package com.dippie.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CatalogMediaAssetRepository extends JpaRepository<CatalogMediaAsset, String> {

    @Query("""
            select asset from CatalogMediaAsset asset
            where asset.id in (
                select link.mediaAssetId from CatalogEpisodeAsset link where link.episodeId = :episodeId
            )
            order by asset.id
            """)
    List<CatalogMediaAsset> findByEpisodeId(@Param("episodeId") String episodeId);
}
