package com.dippie.security;

import java.util.Optional;

// cổng tra user đang hoạt động cho tầng security, tách khỏi JPA entity ở gói user cho khỏi phụ thuộc vòng
@FunctionalInterface
public interface ActiveViewerLookup {

    // check tài khoản còn bật và khớp đúng tokenVersion trong DB
    Optional<Viewer> findActiveViewer(String username, long tokenVersion);

    // bản có thêm profileId để xác thực token hồ sơ trẻ em vẫn thuộc tài khoản đang bật chế độ phụ huynh
    default Optional<Viewer> findActiveViewer(String username, long tokenVersion, Long profileId) {
        if (profileId != null) {
            return Optional.empty();
        }
        return findActiveViewer(username, tokenVersion);
    }

    // tra theo userId kèm tokenVersion lúc check vé phát video để đăng xuất hay khóa nick là vé cũ tịt liền
    default Optional<Viewer> findActiveViewerById(Long userId, long tokenVersion) {
        return Optional.empty();
    }

    // lấy tokenVersion hiện tại của user đang hoạt động để gắn vào vé phát video mới cấp
    default Optional<Long> findActiveTokenVersion(Long userId) {
        return Optional.empty();
    }
}
