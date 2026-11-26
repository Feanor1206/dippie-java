package com.dippie.model.catalog;

import java.util.Locale;

// quy định ai được mở file media: công khai, cần đăng nhập hay chỉ admin
public enum AccessPolicy {
    PUBLIC, AUTHENTICATED, PRIVATE, UNKNOWN;

    // đọc chuỗi policy từ catalog, bỏ trống thì coi là PUBLIC còn chuỗi lạ đẩy về UNKNOWN để chặn luôn
    public static AccessPolicy of(String raw) {
        if (raw == null || raw.isBlank()) {
            return PUBLIC;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "public" -> PUBLIC;
            case "authenticated" -> AUTHENTICATED;
            case "private" -> PRIVATE;
            default -> UNKNOWN;
        };
    }

    // PRIVATE phải vừa đăng nhập vừa là admin, né vụ cờ admin bật nhưng phiên chưa xác thực
    public boolean allows(boolean authenticated, boolean admin) {
        return switch (this) {
            case PUBLIC -> true;
            case AUTHENTICATED -> authenticated;
            case PRIVATE -> authenticated && admin;
            case UNKNOWN -> false;
        };
    }
}
