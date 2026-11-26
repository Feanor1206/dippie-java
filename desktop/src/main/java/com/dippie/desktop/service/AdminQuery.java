package com.dippie.desktop.service;

import com.dippie.desktop.dao.UserDao;
import com.dippie.desktop.db.DataAccessException;
import com.dippie.desktop.model.AdminSession;
import com.dippie.desktop.model.UserAccount;

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
        requireCurrent(session, userDao.findById(session.userId()).orElse(null));
        return delegate.get();
    }

    // Mình chuyển chỗ ném Conflict về tầng service nha Việt, để AdminSession bên model không phải nhập DataAccessException từ db.
    static UserAccount requireCurrent(AdminSession session, UserAccount account) {
        if (!session.matches(account)) {
            throw new DataAccessException.Conflict("Phiên đăng nhập đã hết hiệu lực. Bạn cần đăng nhập lại.");
        }
        return account;
    }
}
