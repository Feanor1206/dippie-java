package com.dippie.dto.quiz;

// client chỉ gửi token câu hỏi và chữ cái lựa chọn (A, B, C hoặc D) bé đã bấm
public record QuizAnswerRequest(String token, String option) {
}
