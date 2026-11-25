package com.dippie.assistant.chat;

import com.dippie.common.error.DomainException;
import org.springframework.http.HttpStatus;

// Kế thừa DomainException để trả HTTP 413 khi body gửi tới /api/chat vượt giới hạn dung lượng.
public class ChatPayloadTooLargeException extends DomainException {

    public ChatPayloadTooLargeException(String message) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, message);
    }
}
