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

// tăng tokenVersion để cắt sạch JWT đang sống của user hiện tại
@Service
public class SessionLogoutService {

    private final CurrentViewer currentViewer;
    private final UserRepository userRepository;
    private final Clock clock;

    public SessionLogoutService(CurrentViewer currentViewer, UserRepository userRepository, Clock clock) {
        this.currentViewer = currentViewer;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    // khóa dòng user trước khi tăng tokenVersion kẻo logout với đổi mật khẩu chạy cùng lúc ghi đè nhau
    @Transactional
    public void logout() {
        Viewer viewer = currentViewer.require();
        User user = userRepository.findByIdForUpdate(viewer.userId())
                .orElseThrow(() -> new UnauthorizedException("Bạn cần đăng nhập để tiếp tục."));
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        user.incrementTokenVersion(now);
        userRepository.saveAndFlush(user);
    }
}
