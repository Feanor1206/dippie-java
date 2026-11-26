package com.dippie.dto.bounty;

import com.dippie.model.bounty.BountySubmission;
import com.dippie.model.bounty.BountySubmissionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

// thông tin 1 lượt bé nộp nhiệm vụ đời thực cho cả phụ huynh và giao diện trẻ em
public record BountySubmissionView(
        Long id,
        Long taskId,
        String taskTitle,
        String taskIcon,
        Long profileId,
        String profileName,
        String profileAvatarKey,
        LocalDate usageDay,
        BountySubmissionStatus status,
        int rewardMinutes,
        LocalDateTime submittedAt,
        LocalDateTime decidedAt,
        String rejectNote
) {

    public static BountySubmissionView of(
            BountySubmission submission,
            String taskTitle,
            String taskIcon,
            String profileName,
            String profileAvatarKey
    ) {
        return new BountySubmissionView(
                submission.getId(),
                submission.getTaskId(),
                taskTitle,
                taskIcon,
                submission.getProfileId(),
                profileName,
                profileAvatarKey,
                submission.getUsageDay(),
                submission.getStatus(),
                submission.getRewardMinutes(),
                submission.getSubmittedAt(),
                submission.getDecidedAt(),
                submission.getRejectNote()
        );
    }
}
