package com.dippie.service.account;

import com.dippie.config.mail.MailProperties;
import com.dippie.model.mail.EmailMessage;

import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

// tách phần soạn mail tiếng Việt khỏi PasswordResetService để sửa câu chữ đỡ đụng logic token
@Component
public class PasswordResetEmailTemplate {

    private final MailProperties mailProperties;

    public PasswordResetEmailTemplate(MailProperties mailProperties) {
        this.mailProperties = mailProperties;
    }

    // dựng mail đặt lại mật khẩu gắn link theo publicBaseUrl, link sống 30 phút
    public EmailMessage build(String recipientEmail, String displayName, String rawToken) {
        String encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String resetLink = mailProperties.normalizedBaseUrl() + "/dat-lai-mat-khau?token=" + encodedToken;
        String greetingName = (displayName == null || displayName.isBlank()) ? "bạn" : displayName.trim();
        String body = "Xin chào " + greetingName + ",\n\n"
                + "Dippie vừa nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.\n"
                + "Bạn mở đường dẫn bên dưới trong vòng 30 phút để tạo mật khẩu mới nhé:\n\n"
                + resetLink + "\n\n"
                + "Đường dẫn này chỉ dùng được một lần. Nếu bạn không yêu cầu đổi mật khẩu, cứ bỏ qua email này là được.\n\n"
                + "Thân mến,\n"
                + "Đội ngũ Dippie\n";
        return new EmailMessage(recipientEmail, "Đặt lại mật khẩu tài khoản Dippie", body);
    }
}
