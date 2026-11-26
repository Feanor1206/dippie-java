package com.dippie.exception;

import org.springframework.http.HttpStatus;

// Lỗi khi body request vượt quá giới hạn dung lượng cho phép của route, trả HTTP 413.
public class RequestBodyTooLargeException extends DomainException {

    public static final String DEFAULT_MESSAGE = "Dữ liệu gửi lên quá lớn.";

    public RequestBodyTooLargeException() {
        super(HttpStatus.PAYLOAD_TOO_LARGE, DEFAULT_MESSAGE);
    }

    public RequestBodyTooLargeException(String message) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, message);
    }
}
