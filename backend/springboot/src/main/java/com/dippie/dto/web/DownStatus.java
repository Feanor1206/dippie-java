package com.dippie.dto.web;

// Phản hồi khi DB không kết nối được.
public record DownStatus(String status) implements HealthStatus {
}
