package com.dippie.exception;

import org.springframework.http.HttpStatus;

// hồ sơ trẻ em đã hết thời gian xem hôm nay, trả 423 Locked
public class ScreenTimeLockedException extends DomainException {

    public ScreenTimeLockedException(String message) {
        super(HttpStatus.LOCKED, message);
    }
}
