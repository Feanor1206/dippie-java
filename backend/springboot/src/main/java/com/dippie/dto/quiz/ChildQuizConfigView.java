package com.dippie.dto.quiz;

import java.util.List;

// cấu hình câu hỏi (lớp, các bộ đang bật) kèm báo cáo tuần cho từng bé
public record ChildQuizConfigView(
        Long profileId,
        String name,
        String avatarKey,
        String grade,
        String gradeLabel,
        String vipGrade,
        String vipGradeLabel,
        List<Long> activeSetIds,
        List<ChildQuizSetRef> activeSets,
        ChildQuizWeeklySummaryView weeklySummary
) {
}
