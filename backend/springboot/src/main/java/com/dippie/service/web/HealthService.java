package com.dippie.service.web;

import com.dippie.config.web.OpenApiConfig;
import com.dippie.dto.web.DownStatus;
import com.dippie.dto.web.HealthStatus;
import com.dippie.dto.web.UpStatus;

import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

// Kiểm tra sống cho backend: thử mở một kết nối DB trong tối đa 2 giây.
// Việc chạm vào DataSource nằm ở service này, controller chỉ đọc kết quả rồi chọn mã HTTP.
@Service
public class HealthService {

    private static final int VALIDATION_TIMEOUT_SECONDS = 2;
    private static final String SERVICE_NAME = "Dippie MovieRCM Backend";

    private final DataSource dataSource;

    public HealthService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public HealthStatus check() {
        return isDatabaseHealthy()
                ? new UpStatus("UP", SERVICE_NAME, OpenApiConfig.API_VERSION)
                : new DownStatus("DOWN");
    }

    private boolean isDatabaseHealthy() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException ex) {
            return false;
        }
    }
}
