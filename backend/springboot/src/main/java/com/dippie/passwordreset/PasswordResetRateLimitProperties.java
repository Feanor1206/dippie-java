package com.dippie.passwordreset;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Cấu hình giới hạn tần suất yêu cầu đặt lại mật khẩu theo IP, mặc định 5 lần trong 15 phút.
@ConfigurationProperties("dippie.password-reset.rate-limit")
public record PasswordResetRateLimitProperties(int maxAttempts, Duration window, int maxClients) {

    public PasswordResetRateLimitProperties {
        if (maxAttempts <= 0) {
            maxAttempts = 5;
        }
        if (window == null || window.isNegative() || window.isZero()) {
            window = Duration.ofMinutes(15);
        }
        if (maxClients <= 0) {
            maxClients = 4096;
        }
    }
}
