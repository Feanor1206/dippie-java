package com.dippie.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dippie.auth.rate-limit")
public record RateLimitProperties(int maxAttempts, Duration window, int maxClients) {

    public RateLimitProperties {
        if (maxAttempts <= 0) {
            maxAttempts = 30;
        }
        if (window == null || window.isNegative() || window.isZero()) {
            window = Duration.ofMinutes(1);
        }
        if (maxClients <= 0) {
            maxClients = 4096;
        }
    }
}
