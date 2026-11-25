package com.dippie.security;

import java.util.Optional;

// Cổng tra cứu tài khoản đang hoạt động cho tầng security, tách hẳn khỏi JPA entity ở gói user để khỏi phụ thuộc vòng.
@FunctionalInterface
public interface ActiveViewerLookup {

    // Kiểm tra tài khoản còn bật và khớp đúng tokenVersion đang lưu trong DB.
    Optional<Viewer> findActiveViewer(String username, long tokenVersion);

    // Tra theo userId kèm tokenVersion khi xác minh vé phát video, để đăng xuất hoặc khóa nick là vé cũ vô hiệu liền.
    default Optional<Viewer> findActiveViewerById(Long userId, long tokenVersion) {
        return Optional.empty();
    }

    // Lấy tokenVersion hiện tại của user đang hoạt động để gắn vào vé phát video mới cấp.
    default Optional<Long> findActiveTokenVersion(Long userId) {
        return Optional.empty();
    }
}
