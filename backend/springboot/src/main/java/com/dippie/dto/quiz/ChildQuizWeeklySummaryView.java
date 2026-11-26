package com.dippie.dto.quiz;

import java.util.List;

// tổng hợp kết quả trả lời 7 ngày qua của 1 hồ sơ bé
public record ChildQuizWeeklySummaryView(
        int days,
        int totalAnswered,
        int correctCount,
        int accuracyPercent,
        int averageResponseMs,
        List<QuizSetWeeklyStatView> bySet,
        List<HardQuestionStatView> hardestQuestions,
        GradeSuggestionView suggestion
) {
}
