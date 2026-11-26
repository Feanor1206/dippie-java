package com.dippie.exception;

import org.springframework.http.HttpStatus;

// kế thừa DomainException để trả 413 khi body gửi tới /api/chat vượt giới hạn dung lượng
public class ChatPayloadTooLargeException extends DomainException {

    public ChatPayloadTooLargeException(String message) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, message);
    }
}
