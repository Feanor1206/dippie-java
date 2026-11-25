package com.dippie.desktop.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;

// Singleton dạng enum để mở kết nối JDBC qua DriverManager, mỗi thao tác tự mở rồi đóng kết nối riêng.
public enum ConnectionProvider {
    INSTANCE;

    // Bọc lỗi JDBC thành câu tiếng Việt gọn gàng; mã 90146 của H2 là báo chưa có tệp DB demo.
    public Connection open(DatabaseConfig config) {
        try {
            return DriverManager.getConnection(config.url(), config.user(), config.password());
        } catch (SQLException exception) {
            String message;
            if (config.url().startsWith("jdbc:h2:file:") && exception.getErrorCode() == 90146) {
                message = "Chưa có cơ sở dữ liệu demo trong thư mục target/dippie-java.";
            } else if (exception instanceof SQLTimeoutException) {
                message = "Kết nối cơ sở dữ liệu quá thời gian chờ.";
            } else {
                message = "Không kết nối được cơ sở dữ liệu. Kiểm tra lại cấu hình kết nối và xem máy chủ đã bật chưa.";
            }
            throw new DataAccessException.Connection(message, exception);
        }
    }
}
