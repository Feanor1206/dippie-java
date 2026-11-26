package com.dippie.service.user;

import com.dippie.model.user.User;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.ActiveViewerLookup;
import com.dippie.security.Viewer;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// kiểu Adapter đổi entity User sang Viewer của ActiveViewerLookup để tầng security khỏi phụ thuộc JPA
@Component
public class UserViewerAdapter implements ActiveViewerLookup {

    private final UserRepository userRepository;

    public UserViewerAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // chỉ nhận JWT khi tài khoản còn bật và tokenVersion khớp y chang DB
    @Override
    @Transactional(readOnly = true)
    public Optional<Viewer> findActiveViewer(String username, long tokenVersion) {
        return userRepository.findByUsername(username)
                .filter(user -> user.isEnabled() && user.getTokenVersion() == tokenVersion)
                .map(user -> new Viewer(user.getId(), user.getUsername(), user.getRole()));
    }

    // bản tra theo userId cho lúc check vé phát video, vẫn soi enabled với tokenVersion để cắt quyền kịp
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

    // lấy tokenVersion hiện tại của tài khoản đang bật để nhét vào vé phát mới cấp
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
