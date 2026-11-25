package com.dippie.user;

import com.dippie.security.Role;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(10)
@ConditionalOnProperty(name = "dippie.seed-demo-data", havingValue = "true")
public class DemoAccountSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountSeeder.class);
    private static final String DEMO_PASSWORD = "password123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DemoAccountSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

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
        if (!userRepository.existsByUsername("user")) {
            userRepository.save(new User(
                    "user",
                    "user@dippie.com",
                    passwordEncoder.encode(DEMO_PASSWORD),
                    "Standard User",
                    Role.ROLE_USER,
                    now
            ));
        }
        log.warn("Demo credentials are active (admin/user with password 'password123'). Do not use in production.");
    }
}
