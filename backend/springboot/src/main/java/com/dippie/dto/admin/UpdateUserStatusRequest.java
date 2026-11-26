package com.dippie.dto.admin;

// Dữ liệu đầu vào mang cờ enabled khi admin bật hoặc khóa tài khoản người dùng.
public record UpdateUserStatusRequest(Boolean enabled) {
}
