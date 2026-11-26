package com.dippie.model.profile;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

// tổng hợp số giây đã xem, thưởng toán, ba mẹ cho thêm, thưởng nhiệm vụ và số giây còn lại trong ngày theo giờ Việt Nam
public record ScreenTimeSummary(
        Long profileId,
        LocalDate day,
        Integer dailyMinutes,
        boolean unlimited,
        int mathLevel,
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

    // chốt ngày sử dụng theo múi giờ Asia/Ho_Chi_Minh để qua 0h là tự làm mới hạn mức
    public static LocalDate todayVietnam(Clock clock) {
        return clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
    }

    public static ScreenTimeSummary of(
            ChildProfile profile,
            LocalDate day,
            long watchedSeconds,
            long rewardSeconds,
            long grantSeconds
    ) {
        return of(profile, day, watchedSeconds, rewardSeconds, grantSeconds, 0L);
    }

    // tính phần còn lại theo công thức hạn mức ngày + thưởng toán + ba mẹ cho + thưởng nhiệm vụ - đã xem
    public static ScreenTimeSummary of(
            ChildProfile profile,
            LocalDate day,
            long watchedSeconds,
            long rewardSeconds,
            long grantSeconds,
            long bountySeconds
    ) {
        long safeWatched = Math.max(0L, watchedSeconds);
        long safeReward = Math.max(0L, rewardSeconds);
        long safeGrant = Math.max(0L, grantSeconds);
        long safeBounty = Math.max(0L, bountySeconds);
        boolean unlimited = profile.isUnlimited();
        long dailySeconds = unlimited ? 0L : profile.getDailyMinutes() * 60L;
        Long remainingSeconds = unlimited
                ? null
                : Math.max(0L, dailySeconds + safeReward + safeGrant + safeBounty - safeWatched);
        boolean locked = !unlimited && remainingSeconds <= 0L;
        long bonusCapSeconds = profile.getMaxBonusMinutes() * 60L;
        long bonusRemainingSeconds = Math.max(0L, bonusCapSeconds - safeReward);
        return new ScreenTimeSummary(
                profile.getId(),
                day,
                profile.getDailyMinutes(),
                unlimited,
                profile.getMathLevel(),
                profile.getRewardMinutes(),
                profile.getMaxBonusMinutes(),
                dailySeconds,
                safeWatched,
                safeReward,
                safeGrant,
                safeBounty,
                remainingSeconds,
                locked,
                bonusCapSeconds,
                bonusRemainingSeconds,
                bonusRemainingSeconds <= 0L
        );
    }
}
