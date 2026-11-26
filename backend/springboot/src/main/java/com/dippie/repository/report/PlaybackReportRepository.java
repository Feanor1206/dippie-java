package com.dippie.repository.report;

import com.dippie.model.report.PlaybackReport;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// repo kéo danh sách báo lỗi có phân trang, kèm hai hàm gỡ user khi xóa tài khoản
public interface PlaybackReportRepository extends JpaRepository<PlaybackReport, Long> {

    List<PlaybackReport> findByStatusOrderByCreatedAtDescIdDesc(String status, Pageable pageable);

    List<PlaybackReport> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    // xóa tài khoản người báo lỗi thì kéo reporterUserId về null
    @Modifying(flushAutomatically = true)
    @Query("update PlaybackReport r set r.reporterUserId = null where r.reporterUserId = :userId")
    int clearReporterUser(@Param("userId") Long userId);

    // xóa tài khoản admin từng xử lý thì kéo resolvedByUserId về null
    @Modifying(flushAutomatically = true)
    @Query("update PlaybackReport r set r.resolvedByUserId = null where r.resolvedByUserId = :userId")
    int clearResolverUser(@Param("userId") Long userId);
}
