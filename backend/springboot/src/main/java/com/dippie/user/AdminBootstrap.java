package com.dippie.user;

import com.dippie.security.PasswordPolicy;
import com.dippie.security.Role;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "password123";
    private static final int SCRAMBLE_BYTES = 32;

    private final BootstrapAdminProperties properties;
    private final boolean seedDemoData;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminBootstrap(
            BootstrapAdminProperties properties,
            @Value("${dippie.seed-demo-data:false}") boolean seedDemoData,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.properties = properties;
        this.seedDemoData = seedDemoData;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (seedDemoData) {
            return;
        }
        String username = trimToEmpty(properties.username());
        String email = trimToEmpty(properties.email());
        String password = properties.password() == null ? "" : properties.password();

        boolean anyConfigured = !username.isEmpty() || !email.isEmpty() || !password.isEmpty();
        if (anyConfigured && (username.isEmpty() || password.isEmpty())) {
            throw new IllegalStateException(
                    "Cần cả APP_BOOTSTRAP_ADMIN_USERNAME và APP_BOOTSTRAP_ADMIN_PASSWORD."
            );
        }
        if (!password.isEmpty()) {
            PasswordPolicy.DEFAULT.validateForAdminBootstrap(password);
        }

        List<User> admins = userRepository.findByRole(Role.ROLE_ADMIN);
        User matchedAdmin = admins.stream()
                .filter(candidate -> candidate.getUsername().equals(username))
                .findFirst()
                .orElse(null);
        List<User> weakAdmins = admins.stream()
                .filter(candidate -> passwordEncoder.matches(DEMO_PASSWORD, candidate.getPassword()))
                .toList();

        LocalDateTime now = LocalDateTime.now(clock);
        if (!weakAdmins.isEmpty()) {
            if (weakAdmins.size() != 1
                    || matchedAdmin == null
                    || !matchedAdmin.getId().equals(weakAdmins.getFirst().getId())
                    || password.isEmpty()) {
                throw new IllegalStateException(
                        "Tài khoản quản trị dùng mật khẩu demo; đặt APP_BOOTSTRAP_ADMIN_USERNAME và APP_BOOTSTRAP_ADMIN_PASSWORD để đổi mật khẩu."
                );
            }
            matchedAdmin.changePassword(passwordEncoder.encode(password), now);
            userRepository.save(matchedAdmin);
        } else if (!password.isEmpty() && matchedAdmin == null) {
            if (!admins.isEmpty()) {
                throw new IllegalStateException(
                        "Đã có quản trị viên; không thể tự động tạo thêm tài khoản quản trị."
                );
            }
            if (email.isEmpty()) {
                throw new IllegalStateException("Cần APP_BOOTSTRAP_ADMIN_EMAIL để tạo quản trị viên.");
            }
            if (userRepository.existsByUsername(username) || userRepository.existsByEmail(email)) {
                throw new IllegalStateException("Tên đăng nhập hoặc email quản trị đã được dùng.");
            }
            User admin = new User(
                    username,
                    email,
                    passwordEncoder.encode(password),
                    null,
                    Role.ROLE_ADMIN,
                    now
            );
            userRepository.save(admin);
        }

        scrambleLegacyDemoUser("user", "user@dippie.com", now);
        scrambleLegacyDemoUser("demo", "demo@dippie.com", now);
    }

    private void scrambleLegacyDemoUser(String username, String email, LocalDateTime now) {
        userRepository.findByUsernameAndEmailAndRole(username, email, Role.ROLE_USER)
                .filter(user -> passwordEncoder.matches(DEMO_PASSWORD, user.getPassword()))
                .ifPresent(user -> {
                    byte[] bytes = new byte[SCRAMBLE_BYTES];
                    secureRandom.nextBytes(bytes);
                    String randomSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                    user.changePassword(passwordEncoder.encode(randomSecret), now);
                    userRepository.save(user);
                });
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
