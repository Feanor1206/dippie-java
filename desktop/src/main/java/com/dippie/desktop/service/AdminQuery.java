package com.dippie.desktop.service;

import com.dippie.desktop.dao.UserDao;
import com.dippie.desktop.model.AdminSession;

import java.util.function.Supplier;

// Protection Proxy chặn trước mọi lượt đọc của quản trị viên để xác minh phiên còn hiệu lực rồi mới gọi service thật.
final class AdminQuery<T> implements Supplier<T> {
    private final UserDao userDao;
    private final AdminSession session;
    private final Supplier<T> delegate;

    AdminQuery(UserDao userDao, AdminSession session, Supplier<T> delegate) {
        this.userDao = userDao;
        this.session = session;
        this.delegate = delegate;
    }

    // Đọc lại tài khoản từ DB mỗi lần để nếu bên web vừa khóa hay hạ quyền thì lệnh đọc bị chặn ngay.
    @Override
    public T get() {
        session.requireCurrent(userDao.findById(session.userId()).orElse(null));
        return delegate.get();
    }
}
