package com.dippie.dto.web;

// trạng thái /api/health, sealed interface Java 21 khóa cứng 2 kiểu UpStatus với DownStatus
// sau này thêm trạng thái mới là compiler bắt sửa hết mấy chỗ switch ngay
public sealed interface HealthStatus permits UpStatus, DownStatus {
    String status();
}
