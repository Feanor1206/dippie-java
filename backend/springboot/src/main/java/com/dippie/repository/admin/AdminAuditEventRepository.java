package com.dippie.repository.admin;

import com.dippie.model.admin.AdminAuditEvent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

// repo cho bảng admin_audit_events
public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, Long> {

    // sort theo createdAt giảm dần kèm id giảm dần kẻo mấy dòng trùng giây bị nhảy thứ tự lúc lật trang
    Page<AdminAuditEvent> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
