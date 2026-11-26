package com.dippie.service.account;

import com.dippie.exception.UnauthorizedException;
import com.dippie.model.user.User;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.CurrentViewer;
import com.dippie.security.Viewer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// Thu hồi toàn bộ JWT đang sống của người dùng hiện tại bằng cách tăng tokenVersion.
@Service
public class SessionLogoutService {

    private static final String UNAUTHENTICATED_MESSAGE = "Bạn cần đăng nhập để tiếp tục.";

    private final CurrentViewer currentViewer;
    private final UserRepository userRepository;
    private final Clock clock;

    public SessionLogoutService(CurrentViewer currentViewer, UserRepository userRepository, Clock clock) {
        this.currentViewer = currentViewer;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    // Khóa dòng user trước khi tăng tokenVersion để hai request đăng xuất hoặc đổi mật khẩu gọi cùng lúc không bị ghi đè nhau.
    @Transactional
    public void logout() {
        Viewer viewer = currentViewer.require();
        User user = userRepository.findByIdForUpdate(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException(UNAUTHENTICATED_MESSAGE));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        user.incrementTokenVersion(now);
        userRepository.saveAndFlush(user);
    }
}
