package com.dippie.dto.quiz;

// payload thêm hoặc sửa thủ công 1 câu hỏi trong bộ câu hỏi của phụ huynh
public record QuizQuestionMutationRequest(
        String pin,
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        String correctOption,
        String explanation,
        String subject,
        String grade,
        Boolean active
) {
}
