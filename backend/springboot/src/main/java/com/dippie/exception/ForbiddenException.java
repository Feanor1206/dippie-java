package com.dippie.exception;

import org.springframework.http.HttpStatus;

// không đủ quyền làm thao tác này, trả 403
public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
