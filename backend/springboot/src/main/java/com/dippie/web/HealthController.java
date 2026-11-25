package com.dippie.web;

import com.dippie.common.web.ApiResponse;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Endpoint kiểm tra sống cho backend và kết nối DB, dùng sealed interface HealthStatus của Java 21 để khóa gọn hai trạng thái UP và DOWN.
@RestController
public class HealthController {

    private static final int VALIDATION_TIMEOUT_SECONDS = 2;
    private static final String SERVICE_NAME = "Dippie MovieRCM Backend";

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // Trả 200 khi DB còn sống, trả 503 khi mất kết nối.
    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<HealthStatus>> health() {
        if (!isDatabaseHealthy()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.failure("Chưa kết nối được cơ sở dữ liệu.", new DownStatus("DOWN")));
        }
        return ResponseEntity.ok(ApiResponse.ok(
                new UpStatus("UP", SERVICE_NAME, OpenApiConfig.API_VERSION),
                "Hệ thống đang hoạt động."
        ));
    }

    private boolean isDatabaseHealthy() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException ex) {
            return false;
        }
    }

    // Chỉ cho phép đúng hai bản ghi UpStatus và DownStatus triển khai.
    public sealed interface HealthStatus permits UpStatus, DownStatus {
        String status();
    }

    // Phản hồi khi hệ thống và DB đều sẵn sàng.
    public record UpStatus(String status, String service, String version) implements HealthStatus {
    }

    // Phản hồi khi DB không kết nối được.
    public record DownStatus(String status) implements HealthStatus {
    }
}
