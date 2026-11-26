package com.dippie.dto.vip;

import java.time.LocalDate;

// kết quả chấm đáp án thử thách mở khóa tập phim VIP kèm đáp án đúng và giải thích
public record VipUnlockResponse(
        boolean correct,
        boolean unlocked,
        String episodeId,
        LocalDate day,
        String correctOption,
        String correctText,
        String explanation
) {
}
