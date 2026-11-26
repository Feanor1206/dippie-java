package com.dippie.service.web;

import com.dippie.config.web.OpenApiConfig;
import com.dippie.dto.web.DownStatus;
import com.dippie.dto.web.HealthStatus;
import com.dippie.dto.web.UpStatus;

import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

// check sức khỏe backend, thử mở 1 kết nối DB trong tối đa 2 giây
// để phần đụng DataSource ở service này cho gọn, controller chỉ đọc kết quả rồi chọn mã HTTP
@Service
public class HealthService {

    private final DataSource dataSource;

    public HealthService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public HealthStatus check() {
        return isDatabaseHealthy()
                ? new UpStatus("UP", "Dippie MovieRCM Backend", OpenApiConfig.API_VERSION)
                : new DownStatus("DOWN");
    }

    private boolean isDatabaseHealthy() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException ex) {
            return false;
        }
    }
}
