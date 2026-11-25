package com.dippie.progress;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WatchProgressRepository extends JpaRepository<WatchProgress, Long> {

    Optional<WatchProgress> findByUserIdAndEpisodeId(Long userId, String episodeId);

    void deleteByUserIdAndEpisodeId(Long userId, String episodeId);

    @Query("""
            select wp, ep, s, sea, ca, ma
            from WatchProgress wp
            left join CatalogEpisode ep on ep.id = wp.episodeId
            left join CatalogSeries s on s.id = ep.seriesId
            left join CatalogSeason sea on sea.id = ep.seasonId
            left join CatalogMediaAsset ca on ca.id = wp.assetId
            left join MediaAsset ma on ma.id = wp.assetId
            where wp.userId = :userId
            order by wp.updatedAt desc
            """)
    List<Object[]> findRecentWithContext(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select new com.dippie.progress.NextEpisodePolicy$Progress(wp.episodeId, wp.positionMs, wp.updatedAt, asset.durationMs)
            from WatchProgress wp join wp.episode ep join wp.catalogAsset asset
            where wp.userId = :userId and ep.seriesId = :seriesId
            """)
    List<NextEpisodePolicy.Progress> findForSeries(@Param("userId") Long userId, @Param("seriesId") String seriesId);

    @Modifying(clearAutomatically = true)
    @Query("""
            update WatchProgress wp
            set wp.assetId = :assetId,
                wp.positionMs = :positionMs,
                wp.optLockVersion = :nextVersion,
                wp.updatedAt = :updatedAt
            where wp.id = :id and wp.optLockVersion = :expectedVersion
            """)
    int advanceVersion(
            @Param("id") Long id,
            @Param("expectedVersion") long expectedVersion,
            @Param("nextVersion") long nextVersion,
            @Param("assetId") String assetId,
            @Param("positionMs") long positionMs,
            @Param("updatedAt") LocalDateTime updatedAt
    );
}
