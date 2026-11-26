package com.dippie.service.user;

import com.dippie.config.user.BootstrapAdminProperties;
import com.dippie.model.user.Role;
import com.dippie.model.user.User;
import com.dippie.repository.user.UserRepository;
import com.dippie.security.PasswordPolicy;
import com.dippie.service.admin.AuditTrailWriter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ApplicationRunner chạy lúc khởi động để tạo tài khoản quản trị đầu tiên, ép đổi mật khẩu admin yếu
// và xáo trộn mật khẩu các tài khoản demo cũ trước khi server nhận request thật.
@Component
public class AdminBootstrap implements ApplicationRunner {

    public static final String ACTION_ADMIN_BOOTSTRAP_UPDATED = "ADMIN_BOOTSTRAP_UPDATED";
    private static final String TARGET_TYPE_USER = "USER";
    private static final int SCRAMBLE_BYTES = 32;
    private static final List<String> KNOWN_WEAK_ADMIN_PASSWORDS = List.of(
            DemoAccountSeeder.DEMO_PASSWORD,
            "password",
            "admin123",
            "123456"
    );

    private final BootstrapAdminProperties properties;
    private final boolean seedDemoData;
    private final Environment environment;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditTrailWriter auditTrailWriter;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public AdminBootstrap(
            BootstrapAdminProperties properties,
            @Value("${dippie.seed-demo-data:false}") boolean seedDemoData,
            Environment environment,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditTrailWriter auditTrailWriter,
            Clock clock
    ) {
        this.properties = properties;
        this.seedDemoData = seedDemoData;
        this.environment = environment;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditTrailWriter = auditTrailWriter;
        this.clock = clock;
    }

    // Constructor rút gọn cho unit test khi không cần Environment và AuditTrailWriter.
    public AdminBootstrap(
            BootstrapAdminProperties properties,
            boolean seedDemoData,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this(properties, seedDemoData, null, userRepository, passwordEncoder, null, clock);
    }

    // Kiểm tra cấu hình bootstrap, buộc đổi mật khẩu admin yếu nếu có và vô hiệu hóa mật khẩu demo cũ.
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Việt giữ điều kiện isDemoOnlyProfile() này: lỡ ai bật dippie.seed-demo-data=true trên mysql/prod
        // thì bước kiểm tra an toàn vẫn phải chạy.
        if (seedDemoData && isDemoOnlyProfile()) {
            return;
        }

        // Đọc biến môi trường bootstrap và kiểm tra đủ cặp username/password cùng chuẩn độ mạnh admin.
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
            PasswordPolicy.DEFAULT.validateBootstrapPassword(password);
        }

        // Rà toàn bộ tài khoản admin hiện có xem có tài khoản nào còn dùng mật khẩu demo hoặc mật khẩu yếu đã biết không.
        List<User> admins = userRepository.findByRole(Role.ROLE_ADMIN);
        User matchedAdmin = admins.stream()
                .filter(candidate -> candidate.getUsername().equals(username))
                .findFirst()
                .orElse(null);
        List<User> weakAdmins = admins.stream()
                .filter(candidate -> hasKnownWeakPassword(candidate.getPassword()))
                .toList();

        // Nếu phát hiện admin mật khẩu yếu thì buộc cấu hình đúng username đó để xoay mật khẩu;
        // còn chưa có admin nào thì mới cho phép tự tạo tài khoản quản trị đầu tiên.
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        if (!weakAdmins.isEmpty()) {
            if (weakAdmins.size() != 1
                    || matchedAdmin == null
                    || !matchedAdmin.getId().equals(weakAdmins.getFirst().getId())
                    || password.isEmpty()) {
                throw new IllegalStateException(
                        "Tài khoản quản trị đang dùng mật khẩu yếu hoặc mật khẩu demo. Hãy đặt APP_BOOTSTRAP_ADMIN_USERNAME và APP_BOOTSTRAP_ADMIN_PASSWORD để đổi ngay."
                );
            }
            matchedAdmin.changePassword(passwordEncoder.encode(password), now);
            User saved = userRepository.save(matchedAdmin);
            recordBootstrapAudit("rotated_weak_admin_password", saved);
        } else if (!password.isEmpty() && matchedAdmin == null) {
            if (!admins.isEmpty()) {
                throw new IllegalStateException(
                        "Hệ thống đã có quản trị viên nên không tự tạo thêm tài khoản mới."
                );
            }
            if (email.isEmpty()) {
                throw new IllegalStateException("Cần APP_BOOTSTRAP_ADMIN_EMAIL để tạo quản trị viên.");
            }
            String validatedUsername = validateBootstrapUsername(username);
            String validatedEmail = validateBootstrapEmail(email);
            if (userRepository.existsByUsername(validatedUsername) || userRepository.existsByEmail(validatedEmail)) {
                throw new IllegalStateException("Tên đăng nhập hoặc email quản trị này đã có người dùng.");
            }
            User admin = new User(
                    validatedUsername,
                    validatedEmail,
                    passwordEncoder.encode(password),
                    null,
                    Role.ROLE_ADMIN,
                    now
            );
            User saved = userRepository.save(admin);
            recordBootstrapAudit("created_bootstrap_admin", saved);
        }

        // Ghi đè mật khẩu ngẫu nhiên cho các tài khoản demo cũ để không ai đăng nhập được bằng mật khẩu mặc định.
        scrambleLegacyDemoUser(DemoAccountSeeder.DEMO_USERNAME, DemoAccountSeeder.DEMO_EMAIL, now);
        scrambleLegacyDemoUser("demo", "demo@dippie.com", now);
    }

    private boolean isDemoOnlyProfile() {
        if (environment == null) {
            return true;
        }
        return environment.acceptsProfiles(Profiles.of("(demo | test) & !mysql & !prod"));
    }

    private boolean hasKnownWeakPassword(String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isEmpty()) {
            return true;
        }
        for (String weakPassword : KNOWN_WEAK_ADMIN_PASSWORDS) {
            if (passwordEncoder.matches(weakPassword, encodedPassword)) {
                return true;
            }
        }
        return false;
    }

    private static String validateBootstrapUsername(String username) {
        try {
            return User.validateUsername(username);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private static String validateBootstrapEmail(String email) {
        try {
            return User.validateEmail(email);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private void recordBootstrapAudit(String operation, User admin) {
        if (auditTrailWriter == null || admin.getId() == null) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("operation", operation);
        details.put("username", admin.getUsername());
        auditTrailWriter.recordForActor(
                null,
                "system",
                ACTION_ADMIN_BOOTSTRAP_UPDATED,
                TARGET_TYPE_USER,
                String.valueOf(admin.getId()),
                details
        );
    }

    private void scrambleLegacyDemoUser(String username, String email, LocalDateTime now) {
        userRepository.findByUsernameAndEmailAndRole(username, email, Role.ROLE_USER)
                .filter(user -> hasKnownWeakPassword(user.getPassword()))
                .ifPresent(user -> {
                    byte[] randomBytes = new byte[SCRAMBLE_BYTES];
                    secureRandom.nextBytes(randomBytes);
                    String randomSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
                    user.changePassword(passwordEncoder.encode(randomSecret), now);
                    userRepository.save(user);
                });
    }

    private static String trimToEmpty(String text) {
        return text == null ? "" : text.trim();
    }
}
