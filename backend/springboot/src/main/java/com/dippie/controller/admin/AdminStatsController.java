package com.dippie.controller.admin;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.admin.AdminStatsResponse;
import com.dippie.service.admin.AdminStatsService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// endpoint trả số liệu tổng quan cho màn hình thống kê admin
@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    public AdminStatsController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    // gom hết số liệu user, phim, phiên xem với mấy bảng xếp hạng trong 1 lần gọi
    @GetMapping
    public ResponseEntity<ApiResponse<AdminStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(adminStatsService.collectStats()));
    }
}
