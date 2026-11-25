package com.dippie.desktop.model;

// Thông tin tài khoản đưa lên bảng người dùng, mang theo tokenVersion để phát hiện xung đột khi có phiên khác sửa trước.
public record UserAccount(long id, String username, String email, String fullName,
                          Role role, boolean enabled, long tokenVersion) {
    public String statusLabel() {
        return enabled ? "Đang hoạt động" : "Đã khóa";
    }
}
