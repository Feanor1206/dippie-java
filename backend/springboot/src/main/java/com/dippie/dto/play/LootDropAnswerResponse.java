package com.dippie.dto.play;

import com.dippie.dto.profile.ScreenTimeStatusView;

// kết quả mở thưởng rương báu kèm đáp án đúng, số phút nhận được, combo và trạng thái giờ xem mới nhất
public record LootDropAnswerResponse(
        boolean correct,
        String correctOption,
        String correctText,
        String explanation,
        int earnedSeconds,
        int earnedMinutes,
        int combo,
        int comboBonusMinutes,
        boolean bonusCapReached,
        ScreenTimeStatusView status
) {
}
