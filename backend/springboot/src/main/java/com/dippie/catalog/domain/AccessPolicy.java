package com.dippie.catalog.domain;

import java.util.Locale;

// Quy định ai được phép xem một tài nguyên media: công khai, cần đăng nhập, hay chỉ admin mới mở được.
public enum AccessPolicy {
    PUBLIC, AUTHENTICATED, PRIVATE, UNKNOWN;

    // Đọc chuỗi policy từ dữ liệu catalog; nếu bỏ trống thì mình coi là PUBLIC, còn chuỗi lạ đẩy về UNKNOWN để chặn lại.
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

    // PRIVATE bắt buộc vừa đã đăng nhập vừa có quyền admin, tránh trường hợp flag admin bật nhưng phiên chưa xác thực.
    public boolean allows(boolean authenticated, boolean admin) {
        return switch (this) {
            case PUBLIC -> true;
            case AUTHENTICATED -> authenticated;
            case PRIVATE -> authenticated && admin;
            case UNKNOWN -> false;
        };
    }
}
