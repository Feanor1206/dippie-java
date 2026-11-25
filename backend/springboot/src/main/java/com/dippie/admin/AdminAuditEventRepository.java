package com.dippie.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, Long> {

    Page<AdminAuditEvent> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
