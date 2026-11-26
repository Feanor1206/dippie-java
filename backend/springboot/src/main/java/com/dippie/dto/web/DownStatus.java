package com.dippie.dto.web;

// DB mất kết nối thì trả cái này
public record DownStatus(String status) implements HealthStatus {
}
