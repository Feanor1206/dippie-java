package com.dippie.config.profile;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// cấu hình giới hạn nhập sai PIN phụ huynh theo tài khoản, mặc định sai 5 lần khóa 5 phút
@ConfigurationProperties("dippie.parent-pin.rate-limit")
public record ParentPinRateLimitProperties(int maxAttempts, Duration window, int maxClients) {

    public ParentPinRateLimitProperties {
        if (maxAttempts <= 0) {
            maxAttempts = 5;
        }
        if (window == null || window.isNegative() || window.isZero()) {
            window = Duration.ofMinutes(5);
        }
        if (maxClients <= 0) {
            maxClients = 4096;
        }
    }
}
