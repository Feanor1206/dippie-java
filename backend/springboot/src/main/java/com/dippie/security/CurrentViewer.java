package com.dippie.security;

import java.util.Optional;

// interface lấy thông tin người đang đăng nhập từ ngữ cảnh request hiện tại
public interface CurrentViewer {

    Optional<Viewer> find();

    // bắt buộc đã đăng nhập, chưa thì ném 401
    Viewer require();

    // bắt buộc là admin, không đúng thì ném 403
    Viewer requireAdmin();

    // bắt buộc đang ở hồ sơ chính, hồ sơ trẻ em gọi vào thì đá 403
    Viewer requireMainProfile(String forbiddenMessage);
}
