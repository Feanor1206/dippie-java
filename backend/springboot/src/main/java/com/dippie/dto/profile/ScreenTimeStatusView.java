package com.dippie.dto.profile;

import com.dippie.model.profile.ScreenTimeSummary;

import java.time.LocalDate;

// trạng thái hạn mức xem trong ngày của hồ sơ hiện tại (hồ sơ chính và hồ sơ không giới hạn luôn limited = false)
public record ScreenTimeStatusView(
        boolean limited,
        boolean unlimited,
        Long profileId,
        LocalDate day,
        Integer dailyMinutes,
        String quizGrade,
        String quizGradeLabel,
        int rewardMinutes,
        int maxBonusMinutes,
        long dailySeconds,
        long watchedSeconds,
        long rewardSeconds,
        long grantSeconds,
        long bountySeconds,
        Long remainingSeconds,
        boolean locked,
        long bonusCapSeconds,
        long bonusRemainingSeconds,
        boolean bonusCapReached
) {

    public static ScreenTimeStatusView from(ScreenTimeSummary summary) {
        return new ScreenTimeStatusView(
                !summary.unlimited(),
                summary.unlimited(),
                summary.profileId(),
                summary.day(),
                summary.dailyMinutes(),
                summary.quizGrade().name(),
                summary.quizGrade().label(),
                summary.rewardMinutes(),
                summary.maxBonusMinutes(),
                summary.dailySeconds(),
                summary.watchedSeconds(),
                summary.rewardSeconds(),
                summary.grantSeconds(),
                summary.bountySeconds(),
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
                true,
                null,
                day,
                null,
                null,
                null,
                0,
                0,
                0L,
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
