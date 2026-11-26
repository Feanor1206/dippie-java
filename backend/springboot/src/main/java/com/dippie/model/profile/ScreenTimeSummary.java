package com.dippie.model.profile;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

// tổng hợp số giây đã xem, thưởng toán, ba mẹ cho thêm và số giây còn lại trong ngày theo giờ Việt Nam
public record ScreenTimeSummary(
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
        long remainingSeconds,
        boolean locked,
        long bonusCapSeconds,
        long bonusRemainingSeconds,
        boolean bonusCapReached
) {

    // chốt ngày sử dụng theo múi giờ Asia/Ho_Chi_Minh để qua 0h là tự làm mới hạn mức
    public static LocalDate todayVietnam(Clock clock) {
        return clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
    }

    // tính phần còn lại theo công thức hạn mức ngày + thưởng toán + ba mẹ cho - đã xem
    public static ScreenTimeSummary of(
            ChildProfile profile,
            LocalDate day,
            long watchedSeconds,
            long rewardSeconds,
            long grantSeconds
    ) {
        long safeWatched = Math.max(0L, watchedSeconds);
        long safeReward = Math.max(0L, rewardSeconds);
        long safeGrant = Math.max(0L, grantSeconds);
        long dailySeconds = profile.getDailyMinutes() * 60L;
        long remainingSeconds = Math.max(0L, dailySeconds + safeReward + safeGrant - safeWatched);
        long bonusCapSeconds = profile.getMaxBonusMinutes() * 60L;
        long bonusRemainingSeconds = Math.max(0L, bonusCapSeconds - safeReward);
        return new ScreenTimeSummary(
                profile.getId(),
                day,
                profile.getDailyMinutes(),
                profile.getMathLevel(),
                profile.getRewardMinutes(),
                profile.getMaxBonusMinutes(),
                dailySeconds,
                safeWatched,
                safeReward,
                safeGrant,
                remainingSeconds,
                remainingSeconds <= 0L,
                bonusCapSeconds,
                bonusRemainingSeconds,
                bonusRemainingSeconds <= 0L
        );
    }
}
