package com.dippie.config.mail;

import com.dippie.service.mail.FileOutboxMailDeliveryStrategy;
import com.dippie.service.mail.MailDeliveryStrategy;
import com.dippie.service.mail.SmtpMailDeliveryStrategy;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;

// factory của Spring chọn Strategy gửi thư tùy theo profile đang chạy và spring.mail.host
@Configuration
public class MailConfiguration {

    // profile demo/test luôn ghi ra file .eml kẻo gửi nhầm thư thật
    // chạy thật mới dùng SMTP khi có spring.mail.host và bean JavaMailSender
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
