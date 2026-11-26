package com.dippie.desktop.model;

// lưu id và tokenVersion của admin sau khi đăng nhập xong
public record AdminSession(long userId, long tokenVersion) {
    // so với bản ghi tài khoản mới nhất xem phiên admin còn hợp lệ không, phần ném lỗi DB để tầng service lo
    public boolean matches(UserAccount account) {
        return account != null && account.id() == userId && account.enabled()
                && account.role() == Role.ROLE_ADMIN && account.tokenVersion() == tokenVersion;
    }
}
