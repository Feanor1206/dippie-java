package com.dippie.security;

import java.util.Optional;

// Giao diện lấy thông tin người đang đăng nhập từ ngữ cảnh request hiện tại.
public interface CurrentViewer {

    Optional<Viewer> find();

    // Bắt buộc đã đăng nhập, chưa thì ném 401.
    Viewer require();

    // Bắt buộc phải là tài khoản quản trị, không đúng thì ném 403.
    Viewer requireAdmin();
}
