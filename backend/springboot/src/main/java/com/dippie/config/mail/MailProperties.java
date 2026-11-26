package com.dippie.config.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

// cấu hình mail đọc từ prefix dippie.mail, tự điền mặc định nếu bỏ trống
@ConfigurationProperties("dippie.mail")
public record MailProperties(String from, String publicBaseUrl, String outboxDir) {

    private static final String DEFAULT_PUBLIC_BASE_URL = "http://localhost:3000";

    public MailProperties {
        from = (from == null || from.isBlank()) ? "no-reply@dippie.local" : from.trim();
        publicBaseUrl = (publicBaseUrl == null || publicBaseUrl.isBlank())
                ? DEFAULT_PUBLIC_BASE_URL
                : publicBaseUrl.trim();
        outboxDir = (outboxDir == null || outboxDir.isBlank()) ? "target/dippie-mail" : outboxDir.trim();
    }

    // cắt dấu / ở cuối publicBaseUrl kẻo lúc ghép link đặt lại mật khẩu bị dính hai dấu "//"
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
