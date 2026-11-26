package com.dippie.desktop.model;

// thông tin user lên bảng người dùng, kèm tokenVersion để phát hiện xung đột khi phiên khác vừa sửa
public record UserAccount(long id, String username, String email, String fullName,
                          Role role, boolean enabled, long tokenVersion) {
    public String statusLabel() {
        return enabled ? "Đang hoạt động" : "Đã khóa";
    }
}
