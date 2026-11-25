package com.dippie.user;

import com.dippie.common.web.ApiResponse;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/health")
    public ResponseEntity<ApiResponse<?>> health() {
        if (!isDatabaseHealthy()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.failure("Không thể kết nối cơ sở dữ liệu.", new DownStatus("DOWN")));
        }
        return ResponseEntity.ok(ApiResponse.ok(
                new UpStatus("UP", "Dippie MovieRCM Backend", "1.0.0"),
                "Dịch vụ đang hoạt động."
        ));
    }

    private boolean isDatabaseHealthy() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException ex) {
            return false;
        }
    }

    public record UpStatus(String status, String service, String version) {
    }

    public record DownStatus(String status) {
    }
}
