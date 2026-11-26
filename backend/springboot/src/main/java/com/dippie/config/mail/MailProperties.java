package com.dippie.config.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

// Bộ cấu hình mail nạp từ tiền tố dippie.mail, tự điền giá trị mặc định nếu người chạy bỏ trống.
@ConfigurationProperties("dippie.mail")
public record MailProperties(String from, String publicBaseUrl, String outboxDir) {

    private static final String DEFAULT_FROM = "no-reply@dippie.local";
    private static final String DEFAULT_PUBLIC_BASE_URL = "http://localhost:3000";
    private static final String DEFAULT_OUTBOX_DIR = "target/dippie-mail";

    public MailProperties {
        from = (from == null || from.isBlank()) ? DEFAULT_FROM : from.trim();
        publicBaseUrl = (publicBaseUrl == null || publicBaseUrl.isBlank())
                ? DEFAULT_PUBLIC_BASE_URL
                : publicBaseUrl.trim();
        outboxDir = (outboxDir == null || outboxDir.isBlank()) ? DEFAULT_OUTBOX_DIR : outboxDir.trim();
    }

    // Cắt dấu gạch chéo ở cuối publicBaseUrl để lúc ghép đường dẫn đặt lại mật khẩu không bị thành hai dấu "//".
    public String normalizedBaseUrl() {
        String baseUrl = publicBaseUrl;
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl.isEmpty() ? DEFAULT_PUBLIC_BASE_URL : baseUrl;
    }

    public Path outboxPath() {
        return Path.of(outboxDir);
    }
}
