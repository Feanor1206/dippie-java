package com.dippie.dto.profile;

import com.dippie.model.profile.ChildProfile;

import java.time.LocalDateTime;

// thông tin công khai của 1 hồ sơ trẻ em trả cho frontend
public record ChildProfileView(
        Long id,
        String name,
        String avatarKey,
        Integer dailyMinutes,
        boolean unlimited,
        String quizGrade,
        String quizGradeLabel,
        int rewardMinutes,
        int maxBonusMinutes,
        LocalDateTime createdAt
) {

    public static ChildProfileView from(ChildProfile profile) {
        return new ChildProfileView(
                profile.getId(),
                profile.getName(),
                profile.getAvatarKey(),
                profile.getDailyMinutes(),
                profile.isUnlimited(),
                profile.getQuizGrade().name(),
                profile.getQuizGrade().label(),
                profile.getRewardMinutes(),
                profile.getMaxBonusMinutes(),
                profile.getCreatedAt()
        );
    }
}
