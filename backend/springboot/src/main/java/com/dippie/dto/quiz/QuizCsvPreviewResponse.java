package com.dippie.dto.quiz;

import java.util.List;

// tóm tắt bản xem trước CSV: số dòng hợp lệ, số dòng lỗi và số câu thực sự sẽ được nhập trong giới hạn 500 câu
public record QuizCsvPreviewResponse(
        int totalRows,
        int validCount,
        int invalidCount,
        long existingQuestionCount,
        int remainingCapacity,
        int willImportCount,
        String headerError,
        List<QuizCsvRowPreview> rows
) {
}
