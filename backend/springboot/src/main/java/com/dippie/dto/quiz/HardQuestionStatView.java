package com.dippie.dto.quiz;

// các câu hỏi bé trả lời sai nhiều nhất trong tuần để ba mẹ ôn lại cùng con
public record HardQuestionStatView(
        String questionText,
        String setName,
        String subjectLabel,
        String gradeLabel,
        int attempts,
        int wrongCount,
        int accuracyPercent
) {
}
