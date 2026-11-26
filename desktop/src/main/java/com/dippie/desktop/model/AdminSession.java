package com.dippie.desktop.model;

// Lưu id và tokenVersion của quản trị viên sau khi đăng nhập thành công.
public record AdminSession(long userId, long tokenVersion) {
    // Đối chiếu với bản ghi tài khoản mới nhất xem phiên quản trị còn hiệu lực không; phần ném lỗi DB để tầng service lo.
    public boolean matches(UserAccount account) {
        return account != null && account.id() == userId && account.enabled()
                && account.role() == Role.ROLE_ADMIN && account.tokenVersion() == tokenVersion;
    }
}
