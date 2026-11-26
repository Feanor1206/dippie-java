package com.dippie.dto.quiz;

import java.util.List;

// câu hỏi mẫu để phụ huynh nhìn thử đề bé sẽ gặp trước khi lưu cấu hình
public record QuizSampleQuestionView(
        String question,
        List<QuizOptionView> options,
        String correctOption,
        String explanation,
        String subject,
        String subjectLabel,
        String grade,
        String gradeLabel,
        String setName
) {
}
