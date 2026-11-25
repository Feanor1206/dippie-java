package com.dippie.progress;

import com.dippie.catalog.domain.CatalogEpisode;
import com.dippie.catalog.domain.CatalogMediaAsset;
import com.dippie.catalog.domain.CatalogSeason;
import com.dippie.catalog.domain.CatalogSeries;
import com.dippie.media.MediaAsset;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Repo cho bảng watch_progress, gồm cả truy vấn join ngữ cảnh và câu UPDATE nguyên tử cho optimistic locking.
public interface WatchProgressRepository extends JpaRepository<WatchProgress, Long> {

    // Chiếu sẵn bản ghi tiến độ cùng tập, bộ, mùa và nguồn phát trong một câu SQL để tránh N+1 khi nạp lịch sử gần đây.
    record ContextRow(
            WatchProgress progress,
            CatalogEpisode episode,
            CatalogSeries series,
            CatalogSeason season,
            CatalogMediaAsset catalogAsset,
            MediaAsset mediaAsset
    ) {
    }

    Optional<WatchProgress> findByUserIdAndEpisodeId(Long userId, String episodeId);

    @Query("select wp.episodeId from WatchProgress wp where wp.userId = :userId")
    List<String> findEpisodeIdsByUserId(@Param("userId") Long userId);

    void deleteByUserIdAndEpisodeId(Long userId, String episodeId);

    // Dùng khi xóa sạch lịch sử xem hoặc xóa tài khoản.
    void deleteByUserId(Long userId);

    // Kéo danh sách xem gần nhất kèm đủ metadata để bước sau lọc ra các tập vẫn còn phát được.
    @Query("""
            select new com.dippie.progress.WatchProgressRepository$ContextRow(wp, ep, s, sea, ca, ma)
            from WatchProgress wp
            left join CatalogEpisode ep on ep.id = wp.episodeId
            left join CatalogSeries s on s.id = ep.seriesId
            left join CatalogSeason sea on sea.id = ep.seasonId
            left join CatalogMediaAsset ca on ca.id = wp.assetId
            left join MediaAsset ma on ma.id = wp.assetId
            where wp.userId = :userId
            order by wp.updatedAt desc
            """)
    List<ContextRow> findRecentWithContext(@Param("userId") Long userId, Pageable pageable);

    // Chỉ lấy tiến độ trỏ tới asset còn tồn tại hoặc nguồn YouTube (youtube:%) để tính tập tiếp theo cho bộ phim.
    @Query("""
            select new com.dippie.progress.NextEpisodePolicy$Progress(
                wp.episodeId, wp.positionMs, wp.updatedAt, asset.durationMs, wp.assetId)
            from WatchProgress wp join wp.episode ep
            left join CatalogMediaAsset asset on asset.id = wp.assetId
            where wp.userId = :userId and ep.seriesId = :seriesId
              and (asset.id is not null or wp.assetId like 'youtube:%')
            """)
    List<NextEpisodePolicy.Progress> findForSeries(@Param("userId") Long userId, @Param("seriesId") String seriesId);

    // Cập nhật có điều kiện theo optLockVersion; bật clearAutomatically để Hibernate xóa cache entity cũ sau khi chạy bulk UPDATE.
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
