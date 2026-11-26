package com.dippie.dto.web;

// Trạng thái trả về của /api/health. Dùng sealed interface của Java 21 nên chỉ có đúng hai bản ghi UpStatus và DownStatus,
// thêm trạng thái mới là compiler bắt sửa mọi chỗ switch ngay.
public sealed interface HealthStatus permits UpStatus, DownStatus {
    String status();
}
