package com.dippie.report;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Repository truy vấn danh sách báo lỗi có giới hạn trang và hỗ trợ gỡ liên kết user khi xóa tài khoản.
public interface PlaybackReportRepository extends JpaRepository<PlaybackReport, Long> {

    List<PlaybackReport> findByStatusOrderByCreatedAtDescIdDesc(String status, Pageable pageable);

    List<PlaybackReport> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    // Gỡ reporterUserId về null khi người báo lỗi yêu cầu xóa tài khoản.
    @Modifying(flushAutomatically = true)
    @Query("update PlaybackReport r set r.reporterUserId = null where r.reporterUserId = :userId")
    int clearReporterUser(@Param("userId") Long userId);

    // Gỡ resolvedByUserId về null khi tài khoản admin từng xử lý bị xóa.
    @Modifying(flushAutomatically = true)
    @Query("update PlaybackReport r set r.resolvedByUserId = null where r.resolvedByUserId = :userId")
    int clearResolverUser(@Param("userId") Long userId);
}
