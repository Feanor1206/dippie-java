package com.dippie.controller.admin;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.admin.AdminStatsResponse;
import com.dippie.service.admin.AdminStatsService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Endpoint trả toàn bộ số liệu tổng quan cho màn hình thống kê quản trị.
@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    public AdminStatsController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    // Gom số liệu người dùng, phim, phiên xem và các bảng xếp hạng nổi bật trong một lần gọi.
    @GetMapping
    public ResponseEntity<ApiResponse<AdminStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.collectStats()));
    }
}
