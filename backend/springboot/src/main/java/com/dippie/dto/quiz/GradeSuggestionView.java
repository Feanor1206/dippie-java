package com.dippie.dto.quiz;

// gợi ý nâng hoặc hạ lớp bằng tiếng Việt dễ hiểu, luôn cần phụ huynh xác nhận PIN
public record GradeSuggestionView(
        String direction,
        String currentGrade,
        String currentGradeLabel,
        String suggestedGrade,
        String suggestedGradeLabel,
        int answeredCount,
        int accuracyPercent,
        String message
) {
}
