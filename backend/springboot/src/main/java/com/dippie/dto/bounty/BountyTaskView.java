package com.dippie.dto.bounty;

import com.dippie.model.bounty.BountyTask;

import java.time.LocalDateTime;
import java.util.List;

// thông tin 1 nhiệm vụ đời thực trả về cho phụ huynh kèm danh sách id hồ sơ bé được gán
public record BountyTaskView(
        Long id,
        String title,
        int rewardMinutes,
        String icon,
        boolean active,
        boolean repeatableDaily,
        List<Long> profileIds,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static BountyTaskView of(BountyTask task, List<Long> profileIds) {
        return new BountyTaskView(
                task.getId(),
                task.getTitle(),
                task.getRewardMinutes(),
                task.getIcon(),
                task.isActive(),
                task.isRepeatableDaily(),
                List.copyOf(profileIds),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
