package com.dippie.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

// Danh sách dải IP/CIDR của reverse proxy được phép gửi X-Forwarded-For, đọc từ dippie.proxy; điền "none" để tắt hoàn toàn.
@ConfigurationProperties("dippie.proxy")
public record TrustedProxyProperties(List<String> trustedCidrs) {

    // Mặc định chỉ tin loopback và các dải mạng nội bộ RFC1918 (như mạng Docker chứa Caddy).
    public static final List<String> DEFAULT_TRUSTED_CIDRS = List.of(
            "127.0.0.1/32",
            "::1/128",
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16"
    );

    public TrustedProxyProperties {
        if (trustedCidrs == null) {
            trustedCidrs = DEFAULT_TRUSTED_CIDRS;
        } else {
            List<String> normalized = trustedCidrs.stream()
                    .filter(cidr -> cidr != null && !cidr.isBlank())
                    .map(String::trim)
                    .toList();
            if (normalized.size() == 1 && "none".equalsIgnoreCase(normalized.getFirst())) {
                trustedCidrs = List.of();
            } else {
                trustedCidrs = normalized.isEmpty() ? DEFAULT_TRUSTED_CIDRS : List.copyOf(normalized);
            }
        }
    }
}
