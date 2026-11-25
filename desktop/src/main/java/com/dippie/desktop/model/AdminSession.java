package com.dippie.desktop.model;

import com.dippie.desktop.db.DataAccessException;

// Lưu id và tokenVersion của quản trị viên sau khi đăng nhập thành công.
public record AdminSession(long userId, long tokenVersion) {
    // Đối chiếu lại với dòng mới nhất trong DB để nếu bên web vừa khóa tài khoản hay thu hồi quyền thì bản desktop chặn ngay.
    public UserAccount requireCurrent(UserAccount account) {
        if (account == null || account.id() != userId || !account.enabled()
                || account.role() != Role.ROLE_ADMIN || account.tokenVersion() != tokenVersion) {
            throw new DataAccessException.Conflict("Phiên đăng nhập đã hết hiệu lực. Bạn cần đăng nhập lại.");
        }
        return account;
    }
}
