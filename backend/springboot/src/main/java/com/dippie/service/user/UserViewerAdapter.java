package com.dippie.service.user;

import com.dippie.model.user.User;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.ActiveViewerLookup;
import com.dippie.security.Viewer;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// Adapter chuyển entity User sang Viewer của tầng security (ActiveViewerLookup) để tầng bảo mật khỏi phụ thuộc JPA.
@Component
public class UserViewerAdapter implements ActiveViewerLookup {

    private final UserRepository userRepository;

    public UserViewerAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Chỉ chấp nhận JWT khi tài khoản còn mở và tokenVersion trùng khớp hoàn toàn với DB.
    @Override
    @Transactional(readOnly = true)
    public Optional<Viewer> findActiveViewer(String username, long tokenVersion) {
        return userRepository.findByUsername(username)
                .filter(user -> user.isEnabled() && user.getTokenVersion() == tokenVersion)
                .map(user -> new Viewer(user.getId(), user.getUsername(), user.getRole()));
    }

    // Bản tra theo userId dùng khi xác minh vé phát video, vẫn đối chiếu enabled và tokenVersion để thu hồi kịp thời.
    @Override
    @Transactional(readOnly = true)
    public Optional<Viewer> findActiveViewerById(Long userId, long tokenVersion) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId)
                .filter(user -> user.isEnabled() && user.getTokenVersion() == tokenVersion)
                .map(user -> new Viewer(user.getId(), user.getUsername(), user.getRole()));
    }

    // Lấy tokenVersion hiện tại của tài khoản đang bật để nhúng vào vé phát video mới cấp.
    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findActiveTokenVersion(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId)
                .filter(User::isEnabled)
                .map(User::getTokenVersion);
    }
}
