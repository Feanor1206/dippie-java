package com.dippie.passwordreset;

import com.dippie.mail.EmailMessage;
import com.dippie.mail.MailProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

// Tách phần soạn email tiếng Việt ra khỏi PasswordResetService để sửa câu chữ không phải đụng vào logic token.
@Component
public class PasswordResetEmailTemplate {

    private static final String SUBJECT = "Đặt lại mật khẩu tài khoản Dippie";
    private static final String RESET_PATH = "/dat-lai-mat-khau?token=";

    private final MailProperties mailProperties;

    public PasswordResetEmailTemplate(MailProperties mailProperties) {
        this.mailProperties = mailProperties;
    }

    // Dựng thư đặt lại mật khẩu gắn đường dẫn theo publicBaseUrl, giữ hiệu lực 30 phút.
    public EmailMessage build(String recipientEmail, String displayName, String rawToken) {
        String encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String resetLink = mailProperties.normalizedBaseUrl() + RESET_PATH + encodedToken;
        String greetingName = (displayName == null || displayName.isBlank()) ? "bạn" : displayName.trim();
        String body = "Xin chào " + greetingName + ",\n\n"
                + "Dippie vừa nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.\n"
                + "Bạn mở đường dẫn bên dưới trong vòng 30 phút để tạo mật khẩu mới nhé:\n\n"
                + resetLink + "\n\n"
                + "Đường dẫn này chỉ dùng được một lần. Nếu bạn không yêu cầu đổi mật khẩu, cứ bỏ qua email này là được.\n\n"
                + "Thân mến,\n"
                + "Đội ngũ Dippie\n";
        return new EmailMessage(recipientEmail, SUBJECT, body);
    }
}
