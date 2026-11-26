package com.dippie.dto.web;

// app với DB đều ổn thì trả cái này
public record UpStatus(String status, String service, String version) implements HealthStatus {
}
