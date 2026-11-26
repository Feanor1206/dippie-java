package com.dippie.dto.vip;

import java.util.List;

// danh sách phim bộ VIP của từng hồ sơ bé hiển thị cho phụ huynh cấu hình
public record ChildProfileVipSeriesView(
        Long profileId,
        String name,
        String avatarKey,
        String quizGrade,
        String quizGradeLabel,
        String vipQuizGrade,
        String vipQuizGradeLabel,
        List<String> seriesIds
) {
}
