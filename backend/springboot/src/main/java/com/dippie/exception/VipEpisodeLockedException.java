package com.dippie.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

// tập phim thuộc bộ VIP chưa được bé mở khóa hôm nay, trả 423 Locked kèm mã lý do
public class VipEpisodeLockedException extends DomainException {

    public VipEpisodeLockedException(String message) {
        super(HttpStatus.LOCKED, message, Map.of("X-Dippie-Lock-Reason", "VIP_EPISODE"));
    }
}
