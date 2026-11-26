package com.dippie.dto.quiz;

// kết quả sau khi nhập CSV vào bộ câu hỏi của phụ huynh
public record QuizCsvImportResponse(
        int importedCount,
        int skippedInvalidCount,
        long totalQuestionsInSet,
        QuizSetView set
) {
}
