package com.dippie.dto.bounty;

import java.util.List;

// payload phụ huynh tạo hoặc sửa một nhiệm vụ đời thực kèm mã PIN và danh sách hồ sơ bé được thấy
public record BountyTaskMutationRequest(
        String pin,
        String title,
        Integer rewardMinutes,
        String icon,
        Boolean active,
        Boolean repeatableDaily,
        List<Long> profileIds
) {
}
