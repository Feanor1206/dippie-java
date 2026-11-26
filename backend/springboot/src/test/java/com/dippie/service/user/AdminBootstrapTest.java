package com.dippie.service.user;

import com.dippie.config.user.BootstrapAdminProperties;
import com.dippie.model.user.Role;
import com.dippie.model.user.User;
import com.dippie.repository.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Xác nhận AdminBootstrap tạo admin đầu tiên, buộc xoay mật khẩu admin yếu và xáo mật khẩu tài khoản demo cũ.
@SpringBootTest
@ActiveProfiles("test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AdminBootstrapTest {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    AdminBootstrapTest(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    // Tạo admin mới khi DB trống, nhưng lần chạy sau không ghi đè mật khẩu của admin đã đặt mật khẩu mạnh.
    @Test
    void bootstrapsAdminAndPreservesExistingStrongAdmin() {
        AdminBootstrap createRunner = new AdminBootstrap(
                new BootstrapAdminProperties("owner", "owner@example.com", "A-strong-private-password-123"),
                false,
                userRepository,
                passwordEncoder,
                clock
        );
        createRunner.run(new DefaultApplicationArguments());

        User created = userRepository.findByUsername("owner").orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.ROLE_ADMIN);
        assertThat(passwordEncoder.matches("A-strong-private-password-123", created.getPassword())).isTrue();

        AdminBootstrap preserveRunner = new AdminBootstrap(
                new BootstrapAdminProperties("owner", "owner@example.com", "another-strong-password-456"),
                false,
                userRepository,
                passwordEncoder,
                clock
        );
        preserveRunner.run(new DefaultApplicationArguments());

        User preserved = userRepository.findByUsername("owner").orElseThrow();
        assertThat(passwordEncoder.matches("A-strong-private-password-123", preserved.getPassword())).isTrue();
    }

    // Chặn khởi động nếu còn admin dùng mật khẩu demo; khi truyền mật khẩu mới hợp lệ thì xoay admin và xáo mật khẩu user/demo cũ.
    @Test
    void requiresExplicitResetForDemoAdminAndScramblesLegacyUsers() {
        DemoAccountSeeder seeder = new DemoAccountSeeder(userRepository, passwordEncoder, clock);
        seeder.run(new DefaultApplicationArguments());
        userRepository.save(new User(
                "demo",
                "demo@dippie.com",
                passwordEncoder.encode("password123"),
                "Demo User",
                Role.ROLE_USER,
                LocalDateTime.now(clock)
        ));

        AdminBootstrap withoutCredentials = new AdminBootstrap(
                new BootstrapAdminProperties("", "", ""),
                false,
                userRepository,
                passwordEncoder,
                clock
        );
        assertThatThrownBy(() -> withoutCredentials.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);

        AdminBootstrap resetRunner = new AdminBootstrap(
                new BootstrapAdminProperties("admin", "", "A-strong-private-password-123"),
                false,
                userRepository,
                passwordEncoder,
                clock
        );
        resetRunner.run(new DefaultApplicationArguments());

        User admin = userRepository.findByUsername("admin").orElseThrow();
        assertThat(passwordEncoder.matches("password123", admin.getPassword())).isFalse();
        assertThat(passwordEncoder.matches("A-strong-private-password-123", admin.getPassword())).isTrue();

        User legacyUser = userRepository.findByUsername("user").orElseThrow();
        User demoUser = userRepository.findByUsername("demo").orElseThrow();
        assertThat(passwordEncoder.matches("password123", legacyUser.getPassword())).isFalse();
        assertThat(passwordEncoder.matches("password123", demoUser.getPassword())).isFalse();
    }

    // Cấu hình thiếu mật khẩu hoặc mật khẩu ngắn dưới 16 ký tự đều phải fail ngay lúc khởi động.
    @Test
    void rejectsIncompleteOrWeakBootstrapConfiguration() {
        AdminBootstrap missingPassword = new AdminBootstrap(
                new BootstrapAdminProperties("owner", "", ""),
                false,
                userRepository,
                passwordEncoder,
                clock
        );
        assertThatThrownBy(() -> missingPassword.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_BOOTSTRAP_ADMIN_PASSWORD");

        AdminBootstrap shortPassword = new AdminBootstrap(
                new BootstrapAdminProperties("owner", "owner@example.com", "short-password"),
                false,
                userRepository,
                passwordEncoder,
                clock
        );
        assertThatThrownBy(() -> shortPassword.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("16");
    }
}
