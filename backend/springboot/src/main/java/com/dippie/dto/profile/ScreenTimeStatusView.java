package com.dippie.dto.profile;

import com.dippie.model.profile.ScreenTimeSummary;

import java.time.LocalDate;

// trạng thái hạn mức xem trong ngày của hồ sơ hiện tại (hồ sơ chính luôn limited = false)
public record ScreenTimeStatusView(
        boolean limited,
        Long profileId,
        LocalDate day,
        int dailyMinutes,
        int mathLevel,
        int rewardMinutes,
        int maxBonusMinutes,
        long dailySeconds,
        long watchedSeconds,
        long rewardSeconds,
        long grantSeconds,
        Long remainingSeconds,
        boolean locked,
        long bonusCapSeconds,
        long bonusRemainingSeconds,
        boolean bonusCapReached
) {

    public static ScreenTimeStatusView from(ScreenTimeSummary summary) {
        return new ScreenTimeStatusView(
                true,
                summary.profileId(),
                summary.day(),
                summary.dailyMinutes(),
                summary.mathLevel(),
                summary.rewardMinutes(),
                summary.maxBonusMinutes(),
                summary.dailySeconds(),
                summary.watchedSeconds(),
                summary.rewardSeconds(),
                summary.grantSeconds(),
                summary.remainingSeconds(),
                summary.locked(),
                summary.bonusCapSeconds(),
                summary.bonusRemainingSeconds(),
                summary.bonusCapReached()
        );
    }

    public static ScreenTimeStatusView unlimited(LocalDate day) {
        return new ScreenTimeStatusView(
                false,
                null,
                day,
                0,
                0,
                0,
                0,
                0L,
                0L,
                0L,
                0L,
                null,
                false,
                0L,
                0L,
                false
        );
    }
}
