package com.dippie.dto.web;

// Phản hồi khi hệ thống và DB đều sẵn sàng.
public record UpStatus(String status, String service, String version) implements HealthStatus {
}
