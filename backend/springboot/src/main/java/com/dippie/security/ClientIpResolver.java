package com.dippie.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

// Tách IP thật của client từ request; mình chỉ đọc X-Forwarded-For khi kết nối trực tiếp đi từ reverse proxy nằm trong dải tin cậy.
@Component
public class ClientIpResolver {

    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";
    private static final String UNKNOWN_IP = "unknown";
    private static final int MAX_IP_TOKEN_LENGTH = 64;
    private static final Pattern IPV4_LITERAL = Pattern.compile(
            "^(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$"
    );
    private static final Pattern IPV6_ALLOWED_CHARS = Pattern.compile("^[0-9a-fA-F:.]+$");

    private final List<CidrBlock> trustedBlocks;

    public ClientIpResolver(TrustedProxyProperties properties) {
        List<CidrBlock> parsed = new ArrayList<>();
        for (String entry : properties.trustedCidrs()) {
            CidrBlock block = CidrBlock.parse(entry);
            if (block != null) {
                parsed.add(block);
            }
        }
        this.trustedBlocks = List.copyOf(parsed);
    }

    // Đọc IP thật của client, tránh bị người dùng tự chèn X-Forwarded-For giả khi gọi thẳng vào backend.
    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN_IP;
        }
        String remoteIp = normalizeIp(request.getRemoteAddr());
        if (remoteIp == null) {
            return UNKNOWN_IP;
        }
        // Gọi thẳng từ IP ngoài danh sách proxy tin cậy thì lấy luôn remoteAddr, bỏ qua mọi header chuyển tiếp.
        if (!isTrustedProxy(remoteIp)) {
            return remoteIp;
        }

        // Duyệt X-Forwarded-For từ phải sang trái để bỏ qua các proxy nội bộ và không bị đánh lừa bởi IP giả ghép ở đầu chuỗi.
        String forwardedFor = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String resolved = resolveFromForwardedFor(forwardedFor);
            if (resolved != null) {
                return resolved;
            }
        }

        String realIp = normalizeIp(request.getHeader(HEADER_X_REAL_IP));
        if (realIp != null) {
            return realIp;
        }
        return remoteIp;
    }

    boolean isTrustedProxy(String ip) {
        byte[] addressBytes = toAddressBytes(ip);
        if (addressBytes == null) {
            return false;
        }
        for (CidrBlock block : trustedBlocks) {
            if (block.matches(addressBytes)) {
                return true;
            }
        }
        return false;
    }

    // Đọc ngược từ hop gần server nhất; gặp IP đầu tiên nằm ngoài dải proxy tin cậy thì lấy làm IP client.
    private String resolveFromForwardedFor(String headerValue) {
        String[] parts = headerValue.split(",");
        String lastTrustedIp = null;
        for (int index = parts.length - 1; index >= 0; index--) {
            String candidate = normalizeIp(parts[index]);
            if (candidate == null) {
                continue;
            }
            if (!isTrustedProxy(candidate)) {
                return candidate;
            }
            lastTrustedIp = candidate;
        }
        return lastTrustedIp;
    }

    // Lọc chỉ nhận chuỗi IPv4/IPv6 thuần trước khi gọi InetAddress để chuỗi rác hay tên miền không kích hoạt DNS lookup.
    private static String normalizeIp(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_IP_TOKEN_LENGTH) {
            return null;
        }
        // Bóc ngoặc vuông kèm cổng ở dạng [::1]:8080.
        if (trimmed.startsWith("[")) {
            int closing = trimmed.indexOf(']');
            if (closing <= 1) {
                return null;
            }
            trimmed = trimmed.substring(1, closing);
        } else if (trimmed.indexOf(':') > 0 && trimmed.indexOf(':') == trimmed.lastIndexOf(':')) {
            // Tách phần cổng nếu là IPv4 dạng a.b.c.d:port.
            trimmed = trimmed.substring(0, trimmed.indexOf(':'));
        }
        // Bỏ zone id của IPv6 link-local (ví dụ %eth0).
        int zoneIndex = trimmed.indexOf('%');
        if (zoneIndex > 0) {
            trimmed = trimmed.substring(0, zoneIndex);
        }
        if (!isLiteralIp(trimmed)) {
            return null;
        }
        byte[] bytes = toAddressBytes(trimmed);
        if (bytes == null) {
            return null;
        }
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private static boolean isLiteralIp(String text) {
        if (IPV4_LITERAL.matcher(text).matches()) {
            return true;
        }
        return text.contains(":") && IPV6_ALLOWED_CHARS.matcher(text).matches();
    }

    private static byte[] toAddressBytes(String literalIp) {
        if (literalIp == null || !isLiteralIp(literalIp)) {
            return null;
        }
        try {
            byte[] raw = InetAddress.getByName(literalIp).getAddress();
            // Đổi IPv6 dạng ánh xạ IPv4 (::ffff:a.b.c.d) về 4 byte để so khớp chung với các dải IPv4.
            if (raw.length == 16 && isIpv4Mapped(raw)) {
                byte[] ipv4 = new byte[4];
                System.arraycopy(raw, 12, ipv4, 0, 4);
                return ipv4;
            }
            return raw;
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    // Biểu diễn một dải mạng CIDR (IPv4 hoặc IPv6) để so khớp IP theo tiền tố bit.
    private record CidrBlock(byte[] networkBytes, int prefixBits) {

        static CidrBlock parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String trimmed = raw.trim();
            int slash = trimmed.indexOf('/');
            String ipPart = slash < 0 ? trimmed : trimmed.substring(0, slash).trim();
            byte[] bytes = toAddressBytes(ipPart);
            if (bytes == null) {
                return null;
            }
            int maxBits = bytes.length * 8;
            int prefix = maxBits;
            if (slash >= 0) {
                try {
                    prefix = Integer.parseInt(trimmed.substring(slash + 1).trim());
                } catch (NumberFormatException ex) {
                    return null;
                }
                if (prefix < 0 || prefix > maxBits) {
                    return null;
                }
            }
            return new CidrBlock(bytes, prefix);
        }

        boolean matches(byte[] candidate) {
            if (candidate.length != networkBytes.length) {
                return false;
            }
            int fullBytes = prefixBits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != networkBytes[i]) {
                    return false;
                }
            }
            int remainingBits = prefixBits % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = (~0 << (8 - remainingBits)) & 0xFF;
            return (candidate[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
        }
    }
}
