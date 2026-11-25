package com.dippie.mail;

import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSender;

// Factory cấu hình Spring chọn Strategy gửi thư tùy theo profile đang chạy và biến spring.mail.host.
@Configuration
public class MailConfiguration {

    // Profile demo và test luôn ghi ra tệp .eml để tránh gửi nhầm thư thật; bản chạy thật dùng SMTP khi có spring.mail.host và bean JavaMailSender.
    @Bean
    public MailDeliveryStrategy mailDeliveryStrategy(
            Environment environment,
            MailProperties mailProperties,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            Clock clock
    ) {
        boolean isDemoOrTest = environment.acceptsProfiles(Profiles.of("demo | test"));
        String mailHost = environment.getProperty("spring.mail.host", "").trim();
        if (isDemoOrTest || mailHost.isEmpty()) {
            return new FileOutboxMailDeliveryStrategy(mailProperties, clock);
        }
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            return new FileOutboxMailDeliveryStrategy(mailProperties, clock);
        }
        return new SmtpMailDeliveryStrategy(mailSender, mailProperties);
    }
}
