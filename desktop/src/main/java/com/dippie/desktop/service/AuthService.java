package com.dippie.desktop.service;

import com.dippie.desktop.dao.UserDao;
import com.dippie.desktop.model.AdminSession;
import com.dippie.desktop.model.Role;
import com.dippie.desktop.model.UserAccount;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;

// Xác thực quản trị viên bằng BCrypt theo đúng quy tắc tài khoản bên web.
public final class AuthService {
    private static final int BCRYPT_MAX_BYTES = 72;
    private final UserDao userDao;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    // BCrypt chỉ nhận tối đa 72 byte UTF-8 (khớp bên web), nên kiểm tra trước để chặn dữ liệu sai ngay từ đầu.
    public static void validateInput(String identifier, String password) {
        if (identifier == null || identifier.isBlank() || password == null || password.isEmpty()
                || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new AuthException(AuthException.Reason.INVALID_CREDENTIALS);
        }
    }

    // Kiểm tra mật khẩu trước rồi mới báo khóa hay thiếu quyền, để người gõ sai mật khẩu không dò ra được trạng thái tài khoản.
    public AdminSession login(String identifier, String password) {
        validateInput(identifier, password);
        var credentials = userDao.findCredentials(identifier.strip())
                .orElseThrow(() -> new AuthException(AuthException.Reason.INVALID_CREDENTIALS));
        if (!matches(password, credentials.passwordHash())) {
            throw new AuthException(AuthException.Reason.INVALID_CREDENTIALS);
        }

        UserAccount account = credentials.account();
        if (!account.enabled()) {
            throw new AuthException(AuthException.Reason.LOCKED);
        }
        if (account.role() != Role.ROLE_ADMIN) {
            throw new AuthException(AuthException.Reason.NOT_ADMIN);
        }
        return new AdminSession(account.id(), account.tokenVersion());
    }

    // Chuỗi hash lỗi định dạng thì coi như sai mật khẩu thay vì để IllegalArgumentException văng ra giao diện.
    private boolean matches(String password, String hash) {
        try {
            return encoder.matches(password, hash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
