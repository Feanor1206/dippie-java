package com.dippie.dto.quiz;

import java.util.List;

// kết quả kiểm tra từng dòng CSV để hiển thị bản xem trước rõ ràng cho phụ huynh
public record QuizCsvRowPreview(
        int rowNumber,
        boolean valid,
        List<String> errors,
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        String correctOption,
        String explanation,
        String subject,
        String subjectLabel,
        String grade,
        String gradeLabel
) {
}
