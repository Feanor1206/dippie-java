package com.dippie.dto.play;

import java.time.LocalDateTime;

// câu hỏi toán nhanh 10 giây khi bé bấm mở rương báu bay ngang
public record LootDropOpenResponse(
        String token,
        String question,
        int mathLevel,
        int timerSeconds,
        LocalDateTime expiresAt,
        int combo,
        int baseRewardMinutes,
        int potentialComboBonusMinutes
) {
}
