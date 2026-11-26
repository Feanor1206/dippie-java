package com.dippie.controller.web;

import com.dippie.common.web.ApiResponse;
import com.dippie.dto.web.DownStatus;
import com.dippie.dto.web.HealthStatus;
import com.dippie.dto.web.UpStatus;
import com.dippie.service.web.HealthService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// endpoint check sống cho backend và kết nối DB, phần kiểm tra thật nằm bên HealthService
// ở đây chỉ switch trên sealed interface HealthStatus để chọn 200 hay 503
@RestController
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    // DB sống thì 200, mất kết nối thì 503
    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<HealthStatus>> health() {
        HealthStatus status = healthService.check();
        return switch (status) {
            case UpStatus up -> ResponseEntity.ok(ApiResponse.ok(up, "Hệ thống đang hoạt động."));
            case DownStatus down -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.failure("Chưa kết nối được cơ sở dữ liệu.", down));
        };
    }
}
