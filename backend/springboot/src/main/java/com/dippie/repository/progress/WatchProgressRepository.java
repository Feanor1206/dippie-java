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

// repo bảng watch_progress, có sẵn mấy câu join ngữ cảnh với UPDATE nguyên tử cho optimistic locking
public interface WatchProgressRepository extends JpaRepository<WatchProgress, Long> {

    // gói tiến độ kèm tập, bộ, mùa với nguồn phát trong một câu SQL để tránh N+1 lúc kéo lịch sử
    record ContextRow(
            WatchProgress progress,
            CatalogEpisode episode,
            CatalogSeries series,
            CatalogSeason season,
            CatalogMediaAsset catalogAsset,
            MediaAsset mediaAsset
    ) {
    }

    Optional<WatchProgress> findByUserIdAndProfileScopeIdAndEpisodeId(Long userId, long profileScopeId, String episodeId);

    // bản cũ tra theo hồ sơ chính (profileScopeId = 0) để giữ tương thích test cũ
    @Query("select wp from WatchProgress wp where wp.userId = :userId and wp.profileScopeId = 0 and wp.episodeId = :episodeId")
    Optional<WatchProgress> findByUserIdAndEpisodeId(@Param("userId") Long userId, @Param("episodeId") String episodeId);

    @Query("select wp.episodeId from WatchProgress wp where wp.userId = :userId and wp.profileScopeId = :profileScopeId")
    List<String> findEpisodeIdsByUserIdAndProfileScopeId(@Param("userId") Long userId, @Param("profileScopeId") long profileScopeId);

    void deleteByUserIdAndProfileScopeIdAndEpisodeId(Long userId, long profileScopeId, String episodeId);

    // xóa sạch lịch sử xem của đúng hồ sơ đang mở hoặc hồ sơ bé bị xóa
    void deleteByUserIdAndProfileScopeId(Long userId, long profileScopeId);

    // gọi lúc xóa tài khoản để dọn hết tiến độ của cả hồ sơ chính lẫn hồ sơ bé
    void deleteByUserId(Long userId);

    // kéo lịch sử xem gần nhất của đúng hồ sơ đang mở kèm đủ metadata để bước sau lọc mấy tập còn phát được
    // kèm wp.id desc làm khóa phụ vì updatedAt cắt xuống giây, lỡ lưu 2 tập cùng giây vẫn xếp tập mới hơn lên đầu
    @Query("""
            select new com.dippie.repository.progress.WatchProgressRepository$ContextRow(wp, ep, s, sea, ca, ma)
            from WatchProgress wp
            left join CatalogEpisode ep on ep.id = wp.episodeId
            left join CatalogSeries s on s.id = ep.seriesId
            left join CatalogSeason sea on sea.id = ep.seasonId
            left join CatalogMediaAsset ca on ca.id = wp.assetId
            left join MediaAsset ma on ma.id = wp.assetId
            where wp.userId = :userId and wp.profileScopeId = :profileScopeId
            order by wp.updatedAt desc, wp.id desc
            """)
    List<ContextRow> findRecentWithContext(
            @Param("userId") Long userId,
            @Param("profileScopeId") long profileScopeId,
            Pageable pageable
    );

    // kéo hết tiến độ trong một bộ của đúng hồ sơ đang mở để NextEpisodeService check quyền với nguồn phát y hệt WatchProgressService
    @Query("""
            select new com.dippie.repository.progress.WatchProgressRepository$ContextRow(wp, ep, s, sea, ca, ma)
            from WatchProgress wp
            join CatalogEpisode ep on ep.id = wp.episodeId
            left join CatalogSeries s on s.id = ep.seriesId
            left join CatalogSeason sea on sea.id = ep.seasonId
            left join CatalogMediaAsset ca on ca.id = wp.assetId
            left join MediaAsset ma on ma.id = wp.assetId
            where wp.userId = :userId and wp.profileScopeId = :profileScopeId and ep.seriesId = :seriesId
            order by wp.updatedAt desc, wp.id desc
            """)
    List<ContextRow> findForSeriesWithContext(
            @Param("userId") Long userId,
            @Param("profileScopeId") long profileScopeId,
            @Param("seriesId") String seriesId
    );

    // UPDATE có điều kiện theo optLockVersion, bật clearAutomatically để Hibernate xả cache entity cũ sau bulk UPDATE
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
