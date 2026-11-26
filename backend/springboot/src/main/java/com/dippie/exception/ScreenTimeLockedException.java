package com.dippie.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

// hồ sơ trẻ em đã hết thời gian xem hôm nay, trả 423 Locked kèm mã lý do
public class ScreenTimeLockedException extends DomainException {

    public ScreenTimeLockedException(String message) {
        super(HttpStatus.LOCKED, message, Map.of("X-Dippie-Lock-Reason", "SCREEN_TIME"));
    }
}
