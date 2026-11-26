package com.dippie.controller.admin;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.admin.AdminAuditEventView;
import com.dippie.dto.admin.AdminPageResponse;
import com.dippie.service.admin.AdminAuditService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Mở endpoint cho admin xem lại lịch sử thao tác quản trị theo từng trang.
@RestController
@RequestMapping("/api/admin/audit-events")
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    public AdminAuditController(AdminAuditService adminAuditService) {
        this.adminAuditService = adminAuditService;
    }

    // Trả về các sự kiện mới nhất trước, mặc định mỗi trang 20 dòng.
    @GetMapping
    public ResponseEntity<ApiResponse<AdminPageResponse<AdminAuditEventView>>> listEvents(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(adminAuditService.listEvents(page, size)));
    }
}
