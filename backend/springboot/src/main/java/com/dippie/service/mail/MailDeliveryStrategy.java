package com.dippie.service.mail;

import com.dippie.model.mail.EmailMessage;

// kiểu Strategy tách cách gửi mail khỏi service gọi, cần đổi giữa SMTP thật với file .eml là xong
public interface MailDeliveryStrategy {

    // gửi mail theo Strategy đang bật
    void deliver(EmailMessage message);
}
