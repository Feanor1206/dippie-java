package com.dippie.dto.quiz;

// payload xác nhận nhập các dòng CSV hợp lệ vào bộ câu hỏi kèm mã PIN phụ huynh
public record QuizCsvImportRequest(String pin, String csvContent) {
}
