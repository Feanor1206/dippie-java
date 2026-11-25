package com.dippie.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

// Cài đặt Strategy gửi email thật qua máy chủ SMTP bằng JavaMailSender của Spring.
public class SmtpMailDeliveryStrategy implements MailDeliveryStrategy {

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    public SmtpMailDeliveryStrategy(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    // Đóng gói MimeMessage dùng UTF-8 để giữ nguyên dấu tiếng Việt rồi gửi qua SMTP.
    @Override
    public void deliver(EmailMessage message) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            messageHelper.setFrom(properties.from());
            messageHelper.setTo(message.to());
            messageHelper.setSubject(message.subject());
            messageHelper.setText(message.body(), false);
            mailSender.send(mimeMessage);
        } catch (MessagingException | MailException ex) {
            throw new IllegalStateException("Không thể gửi email qua máy chủ SMTP.", ex);
        }
    }
}
