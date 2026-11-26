package com.dippie.dto.quiz;

// thống kê tuần theo từng bộ câu hỏi của bé
public record QuizSetWeeklyStatView(
        Long setId,
        String setName,
        int answered,
        int correct,
        int accuracyPercent
) {
}
