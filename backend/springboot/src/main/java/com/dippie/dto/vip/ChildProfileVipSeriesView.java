package com.dippie.dto.vip;

import java.util.List;

// danh sách phim bộ VIP của từng hồ sơ bé hiển thị cho phụ huynh cấu hình
public record ChildProfileVipSeriesView(
        Long profileId,
        String name,
        String avatarKey,
        int mathLevel,
        int vipMathLevel,
        List<String> seriesIds
) {
}
