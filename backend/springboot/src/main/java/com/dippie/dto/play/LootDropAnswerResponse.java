package com.dippie.dto.play;

import com.dippie.dto.profile.ScreenTimeStatusView;

// kết quả mở thưởng rương báu kèm số phút nhận được, combo và trạng thái giờ xem mới nhất
public record LootDropAnswerResponse(
        boolean correct,
        int earnedSeconds,
        int earnedMinutes,
        int combo,
        int comboBonusMinutes,
        boolean bonusCapReached,
        ScreenTimeStatusView status
) {
}
