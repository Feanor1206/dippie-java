package com.dippie.dto.vault;

import java.time.LocalDate;
import java.util.List;

// trạng thái két sắt phim cuối tuần và thanh tiến độ Vé xem phim theo giờ Việt Nam
public record WeekendVaultStatusView(
        boolean enabled,
        Long profileId,
        LocalDate day,
        LocalDate weekStart,
        boolean weekend,
        int progressPercent,
        boolean unlocked,
        boolean manualUnlocked,
        boolean locked,
        List<WeekendVaultDayView> days
) {

    public static WeekendVaultStatusView unrestricted(LocalDate day, LocalDate weekStart, boolean weekend) {
        return new WeekendVaultStatusView(
                false,
                null,
                day,
                weekStart,
                weekend,
                100,
                true,
                false,
                false,
                List.of()
        );
    }
}
