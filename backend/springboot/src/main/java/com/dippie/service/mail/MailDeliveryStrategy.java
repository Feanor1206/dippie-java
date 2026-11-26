package com.dippie.service.mail;

import com.dippie.model.mail.EmailMessage;

// Giao diện Strategy tách cách gửi email khỏi các service gọi nó, nhờ vậy dễ chuyển giữa SMTP thật và hộp thư .eml.
public interface MailDeliveryStrategy {

    // Gửi một bức thư theo cài đặt Strategy đang được nạp.
    void deliver(EmailMessage message);
}
