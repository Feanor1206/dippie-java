package com.dippie.desktop.db;

// giữ 3 thông số JDBC để mở kết nối, ghi đè toString() che URL với mật khẩu phòng lỡ in ra log
public record DatabaseConfig(String url, String user, String password) {
    @Override
    public String toString() {
        return "Cấu hình cơ sở dữ liệu";
    }
}
