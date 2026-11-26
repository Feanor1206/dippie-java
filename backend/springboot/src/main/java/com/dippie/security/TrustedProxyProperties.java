package com.dippie.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// danh sách IP/CIDR của reverse proxy được phép gửi X-Forwarded-For từ dippie.proxy, điền "none" là tắt hết
@ConfigurationProperties("dippie.proxy")
public record TrustedProxyProperties(List<String> trustedCidrs) {

    // mặc định chỉ tin loopback và mấy dải nội bộ RFC1918 (như mạng Docker chứa Caddy)
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
