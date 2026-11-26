package com.dippie.dto.vip;

import java.time.LocalDate;

// kết quả chấm đáp án thử thách mở khóa tập phim VIP
public record VipUnlockResponse(
        boolean correct,
        boolean unlocked,
        String episodeId,
        LocalDate day
) {
}
