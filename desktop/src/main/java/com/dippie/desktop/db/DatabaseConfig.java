package com.dippie.desktop.db;

// Giữ 3 thông số JDBC để mở kết nối. Mình ghi đè toString() để che URL và mật khẩu phòng lúc lỡ in ra log.
public record DatabaseConfig(String url, String user, String password) {
    @Override
    public String toString() {
        return "Cấu hình cơ sở dữ liệu";
    }
}
