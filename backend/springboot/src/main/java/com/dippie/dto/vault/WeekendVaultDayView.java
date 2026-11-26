package com.dippie.dto.vault;

import java.time.LocalDate;

// tiến độ tích Vé xem phim cho từng ngày thứ Hai đến thứ Sáu trong tuần
public record WeekendVaultDayView(
        LocalDate day,
        int dayOfWeek,
        boolean ended,
        boolean stoppedInTime,
        long mathRewardCount,
        boolean mathGoalMet,
        int earnedPercent
) {
}
