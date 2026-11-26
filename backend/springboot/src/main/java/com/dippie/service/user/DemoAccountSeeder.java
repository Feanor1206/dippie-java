package com.dippie.service.user;

import com.dippie.model.user.Role;
import com.dippie.model.user.User;
import com.dippie.repository.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

// Tạo sẵn tài khoản admin và user mẫu cho môi trường demo/test.
// Mình đặt @Order(10) để chạy trước DemoHistorySeeder (@Order(20)) và khóa profile không bao giờ chạy cùng mysql/prod.
@Component
@Order(10)
@Profile("(demo | test) & !mysql & !prod")
@ConditionalOnProperty(name = "dippie.seed-demo-data", havingValue = "true")
public class DemoAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountSeeder.class);
    public static final String DEMO_USERNAME = "user";
    public static final String DEMO_EMAIL = "user@dippie.com";
    public static final String DEMO_PASSWORD = "password123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DemoAccountSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    // Chỉ chèn hai tài khoản mẫu nếu chưa có sẵn trong DB.
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(new User(
                    "admin",
                    "admin@dippie.com",
                    passwordEncoder.encode(DEMO_PASSWORD),
                    "System Administrator",
                    Role.ROLE_ADMIN,
                    now
            ));
        }
        if (!userRepository.existsByUsername(DEMO_USERNAME)) {
            userRepository.save(new User(
                    DEMO_USERNAME,
                    DEMO_EMAIL,
                    passwordEncoder.encode(DEMO_PASSWORD),
                    "Standard User",
                    Role.ROLE_USER,
                    now
            ));
        }
        log.warn("Đang bật tài khoản demo (admin/user, mật khẩu 'password123'). Không dùng cấu hình này trên môi trường thật.");
    }
}
