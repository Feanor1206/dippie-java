package com.dippie.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

// Repository thao tác với bảng admin_audit_events.
public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, Long> {

    // Sắp xếp theo thời gian tạo giảm dần kèm id giảm dần để các bản ghi trùng giây không bị nhảy thứ tự khi chuyển trang.
    Page<AdminAuditEvent> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
