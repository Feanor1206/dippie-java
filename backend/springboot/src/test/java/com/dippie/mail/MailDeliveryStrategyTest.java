package com.dippie.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dippie.admin.AuditTrailWriter;
import com.dippie.common.error.InvalidRequestException;
import com.dippie.passwordreset.PasswordResetConfirmRequest;
import com.dippie.passwordreset.PasswordResetEmailTemplate;
import com.dippie.passwordreset.PasswordResetRateLimitProperties;
import com.dippie.passwordreset.PasswordResetRateLimiter;
import com.dippie.passwordreset.PasswordResetService;
import com.dippie.passwordreset.PasswordResetToken;
import com.dippie.passwordreset.PasswordResetTokenRepository;
import com.dippie.passwordreset.ResetTokenCodec;
import com.dippie.security.Role;
import com.dippie.user.User;
import com.dippie.user.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

// Unit test cho các Strategy gửi mail (.eml và SMTP), Factory trong MailConfiguration và đúng mốc hết hạn 30 phút của token đặt lại mật khẩu.
class MailDeliveryStrategyTest {

    // Xác nhận Factory luôn chọn hộp thư .eml ở profile demo hoặc test và khi thiếu spring.mail.host; chỉ dùng SMTP ở prod khi có host.
    @Test
    void selectsFileOutboxOrSmtpStrategyAccordingToProfileAndMailHost(@TempDir Path tempDir) {
        MailConfiguration mailConfiguration = new MailConfiguration();
        MailProperties mailProperties = new MailProperties("no-reply@dippie.vn", "https://dippie.vn", tempDir.toString());
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-26T01:02:03Z"), ZoneOffset.UTC);
        JavaMailSender sender = mock(JavaMailSender.class);
        ObjectProvider<JavaMailSender> provider = providerOf(sender);

        MockEnvironment demoWithHost = new MockEnvironment().withProperty("spring.mail.host", "smtp.example.com");
        demoWithHost.setActiveProfiles("demo");
        assertThat(mailConfiguration.mailDeliveryStrategy(demoWithHost, mailProperties, provider, fixedClock))
                .isInstanceOf(FileOutboxMailDeliveryStrategy.class);

        MockEnvironment testWithHost = new MockEnvironment().withProperty("spring.mail.host", "smtp.example.com");
        testWithHost.setActiveProfiles("test");
        assertThat(mailConfiguration.mailDeliveryStrategy(testWithHost, mailProperties, provider, fixedClock))
                .isInstanceOf(FileOutboxMailDeliveryStrategy.class);

        MockEnvironment prodEmptyHost = new MockEnvironment().withProperty("spring.mail.host", "   ");
        prodEmptyHost.setActiveProfiles("prod");
        assertThat(mailConfiguration.mailDeliveryStrategy(prodEmptyHost, mailProperties, provider, fixedClock))
                .isInstanceOf(FileOutboxMailDeliveryStrategy.class);

        MockEnvironment prodWithHost = new MockEnvironment().withProperty("spring.mail.host", "smtp.dippie.vn");
        prodWithHost.setActiveProfiles("prod");
        assertThat(mailConfiguration.mailDeliveryStrategy(prodWithHost, mailProperties, provider, fixedClock))
                .isInstanceOf(SmtpMailDeliveryStrategy.class);
    }

    // Kiểm tra tệp .eml giữ nguyên tiếng Việt UTF-8 và tự thêm hậu tố khi hai email tạo trùng mili giây.
    @Test
    void writesUtf8EmlFilesWithTimestampAndRecipientAndHandlesSameMillisecondCollision(@TempDir Path tempDir)
            throws Exception {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-26T05:06:07.890Z"), ZoneOffset.UTC);
        MailProperties mailProperties = new MailProperties("no-reply@dippie.vn", "https://dippie.vn", tempDir.toString());
        FileOutboxMailDeliveryStrategy strategy = new FileOutboxMailDeliveryStrategy(mailProperties, fixedClock);

        EmailMessage message = new EmailMessage(
                "gia.dinh@example.com",
                "Đặt lại mật khẩu tài khoản Dippie",
                "Xin chào gia đình,\nhttps://dippie.vn/dat-lai-mat-khau?token=abc123\n"
        );
        strategy.deliver(message);
        strategy.deliver(message);

        List<Path> files;
        try (Stream<Path> stream = Files.list(tempDir)) {
            files = stream.toList();
        }
        assertThat(files)
                .extracting(path -> path.getFileName().toString())
                .containsExactlyInAnyOrder(
                        "20261126T050607890Z-gia.dinh@example.com.eml",
                        "20261126T050607890Z-gia.dinh@example.com-1.eml"
                );
        assertThat(new MailProperties(null, null, null).outboxPath()).isEqualTo(Path.of("target/dippie-mail"));

        Path primaryFile = tempDir.resolve("20261126T050607890Z-gia.dinh@example.com.eml");
        String content = Files.readString(primaryFile, StandardCharsets.UTF_8);
        assertThat(content).contains("From: no-reply@dippie.vn");
        assertThat(content).contains("To: gia.dinh@example.com");
        assertThat(content).contains("Subject: Đặt lại mật khẩu tài khoản Dippie");
        assertThat(content).contains("https://dippie.vn/dat-lai-mat-khau?token=abc123");
    }

    // Kiểm tra nhánh SMTP dựng MimeMessage UTF-8 rồi gọi JavaMailSender.send.
    @Test
    void deliversMessageThroughJavaMailSenderWhenUsingSmtpStrategy() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        MailProperties mailProperties = new MailProperties("no-reply@dippie.vn", "https://dippie.vn", "target/dippie-mail");
        SmtpMailDeliveryStrategy strategy = new SmtpMailDeliveryStrategy(mailSender, mailProperties);

        strategy.deliver(new EmailMessage(
                "viewer@example.com",
                "Đặt lại mật khẩu tài khoản Dippie",
                "Nội dung tiếng Việt có dấu"
        ));

        verify(mailSender).send(mimeMessage);
    }

    // Chạy đồng hồ giả tới đúng phút thứ 30 để chắc chắn token bị từ chối ngay tại ranh giới hết hạn.
    @Test
    void rejectsTokenAtExactThirtyMinuteExpiryBoundary() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-26T00:00:00Z"));
        ResetTokenCodec codec = new ResetTokenCodec();
        String rawToken = codec.generateRawToken();
        String tokenHash = codec.hash(rawToken);

        LocalDateTime createdAt = LocalDateTime.now(clock);
        PasswordResetToken stored = new PasswordResetToken(7L, tokenHash, createdAt.plusMinutes(30), createdAt);

        UserRepository userRepository = mock(UserRepository.class);
        PasswordResetTokenRepository tokenRepository = mock(PasswordResetTokenRepository.class);
        when(tokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(stored));

        PasswordResetService service = new PasswordResetService(
                userRepository,
                tokenRepository,
                codec,
                new PasswordResetEmailTemplate(new MailProperties(null, null, null)),
                message -> { },
                new PasswordResetRateLimiter(new PasswordResetRateLimitProperties(5, Duration.ofMinutes(15), 100), clock),
                new BCryptPasswordEncoder(),
                mock(AuditTrailWriter.class),
                clock
        );

        // Tua đồng hồ đúng 30 phút để kiểm tra mốc biên hết hạn.
        clock.advance(Duration.ofMinutes(30));

        assertThatThrownBy(() -> service.confirmReset(new PasswordResetConfirmRequest(rawToken, "newpass123")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage(PasswordResetService.INVALID_OR_EXPIRED_TOKEN_MESSAGE);
    }

    // Ở mốc 29 phút 59 giây token vẫn còn hợp lệ và đổi mật khẩu xong phải tăng token_version.
    @Test
    void acceptsTokenJustBeforeThirtyMinuteExpiryBoundary() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-26T00:00:00Z"));
        ResetTokenCodec codec = new ResetTokenCodec();
        String rawToken = codec.generateRawToken();
        String tokenHash = codec.hash(rawToken);

        LocalDateTime createdAt = LocalDateTime.now(clock);
        PasswordResetToken stored = new PasswordResetToken(7L, tokenHash, createdAt.plusMinutes(30), createdAt);
        User user = new User("viewer", "viewer@example.com", "old", "Viewer", Role.ROLE_USER, createdAt);

        UserRepository userRepository = mock(UserRepository.class);
        PasswordResetTokenRepository tokenRepository = mock(PasswordResetTokenRepository.class);
        when(tokenRepository.findByTokenHashForUpdate(tokenHash)).thenReturn(Optional.of(stored));
        when(userRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(tokenRepository.saveAndFlush(stored)).thenReturn(stored);

        PasswordResetService service = new PasswordResetService(
                userRepository,
                tokenRepository,
                codec,
                new PasswordResetEmailTemplate(new MailProperties(null, null, null)),
                message -> { },
                new PasswordResetRateLimiter(new PasswordResetRateLimitProperties(5, Duration.ofMinutes(15), 100), clock),
                new BCryptPasswordEncoder(),
                mock(AuditTrailWriter.class),
                clock
        );

        clock.advance(Duration.ofMinutes(29).plusSeconds(59));
        service.confirmReset(new PasswordResetConfirmRequest(rawToken, "newpass123"));

        assertThat(stored.isUsed()).isTrue();
        assertThat(user.getTokenVersion()).isEqualTo(1L);
    }

    private static ObjectProvider<JavaMailSender> providerOf(JavaMailSender sender) {
        return new ObjectProvider<>() {
            @Override
            public JavaMailSender getObject() {
                return sender;
            }

            @Override
            public JavaMailSender getObject(Object... args) {
                return sender;
            }

            @Override
            public JavaMailSender getIfAvailable() {
                return sender;
            }

            @Override
            public JavaMailSender getIfUnique() {
                return sender;
            }
        };
    }

    // Đồng hồ giả cho phép tua thời gian trong test để kiểm tra đúng ranh giới hết hạn token.
    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant initial) {
            this.current = initial;
        }

        private void advance(Duration duration) {
            this.current = this.current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
