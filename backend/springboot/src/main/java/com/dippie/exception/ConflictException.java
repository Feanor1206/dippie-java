package com.dippie.exception;

import org.springframework.http.HttpStatus;

// lỗi trùng hoặc xung đột dữ liệu (như trùng username hay email), trả 409
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
