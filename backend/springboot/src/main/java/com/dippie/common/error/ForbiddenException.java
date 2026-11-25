package com.dippie.common.error;

import org.springframework.http.HttpStatus;

// Lỗi không đủ quyền thực hiện thao tác, trả HTTP 403.
public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
