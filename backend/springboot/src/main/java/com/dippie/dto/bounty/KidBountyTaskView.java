package com.dippie.dto.bounty;

import com.dippie.model.bounty.BountyTask;

// thẻ nhiệm vụ hiển thị trong chế độ trẻ em kèm trạng thái nộp hiện tại của riêng bé
public record KidBountyTaskView(
        Long id,
        String title,
        int rewardMinutes,
        String icon,
        boolean repeatableDaily,
        String state,
        Long pendingSubmissionId
) {

    public static KidBountyTaskView of(BountyTask task, String state, Long pendingSubmissionId) {
        return new KidBountyTaskView(
                task.getId(),
                task.getTitle(),
                task.getRewardMinutes(),
                task.getIcon(),
                task.isRepeatableDaily(),
                state,
                pendingSubmissionId
        );
    }
}
