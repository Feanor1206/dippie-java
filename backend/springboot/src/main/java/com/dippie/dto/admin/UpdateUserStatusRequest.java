package com.dippie.dto.admin;

// body gửi cờ enabled lúc admin mở hoặc khóa tài khoản
public record UpdateUserStatusRequest(Boolean enabled) {
}
