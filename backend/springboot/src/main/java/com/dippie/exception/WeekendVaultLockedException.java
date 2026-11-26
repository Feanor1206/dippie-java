package com.dippie.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

// phim lẻ bị khóa trong két sắt phim cuối tuần cho hồ sơ trẻ em, trả 423 Locked kèm mã lý do
public class WeekendVaultLockedException extends DomainException {

    public WeekendVaultLockedException(String message) {
        super(HttpStatus.LOCKED, message, Map.of("X-Dippie-Lock-Reason", "WEEKEND_VAULT"));
    }
}
