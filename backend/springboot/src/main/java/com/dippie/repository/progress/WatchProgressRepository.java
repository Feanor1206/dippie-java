package com.dippie.repository.progress;

import com.dippie.model.catalog.CatalogEpisode;
import com.dippie.model.catalog.CatalogMediaAsset;
import com.dippie.model.catalog.CatalogSeason;
import com.dippie.model.catalog.CatalogSeries;
import com.dippie.model.media.MediaAsset;
import com.dippie.model.progress.WatchProgress;

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
    // Thêm wp.id desc làm khóa phụ vì updatedAt cắt theo giây, lỡ lưu 2 tập trong cùng một giây vẫn giữ đúng tập mới hơn lên đầu.
    @Query("""
            select new com.dippie.repository.progress.WatchProgressRepository$ContextRow(wp, ep, s, sea, ca, ma)
            from WatchProgress wp
            left join CatalogEpisode ep on ep.id = wp.episodeId
            left join CatalogSeries s on s.id = ep.seriesId
            left join CatalogSeason sea on sea.id = ep.seasonId
            left join CatalogMediaAsset ca on ca.id = wp.assetId
            left join MediaAsset ma on ma.id = wp.assetId
            where wp.userId = :userId
            order by wp.updatedAt desc, wp.id desc
            """)
    List<ContextRow> findRecentWithContext(@Param("userId") Long userId, Pageable pageable);

    // Lấy toàn bộ tiến độ trong một bộ phim kèm ngữ cảnh phát để NextEpisodeService kiểm tra quyền và nguồn phát giống hệt WatchProgressService.
    @Query("""
            select new com.dippie.repository.progress.WatchProgressRepository$ContextRow(wp, ep, s, sea, ca, ma)
            from WatchProgress wp
            join CatalogEpisode ep on ep.id = wp.episodeId
            left join CatalogSeries s on s.id = ep.seriesId
            left join CatalogSeason sea on sea.id = ep.seasonId
            left join CatalogMediaAsset ca on ca.id = wp.assetId
            left join MediaAsset ma on ma.id = wp.assetId
            where wp.userId = :userId and ep.seriesId = :seriesId
            order by wp.updatedAt desc, wp.id desc
            """)
    List<ContextRow> findForSeriesWithContext(@Param("userId") Long userId, @Param("seriesId") String seriesId);

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
