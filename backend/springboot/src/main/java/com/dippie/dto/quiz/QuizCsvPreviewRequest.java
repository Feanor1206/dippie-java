package com.dippie.dto.quiz;

// payload gửi nội dung CSV để kiểm tra trước khi lưu
public record QuizCsvPreviewRequest(String csvContent) {
}
