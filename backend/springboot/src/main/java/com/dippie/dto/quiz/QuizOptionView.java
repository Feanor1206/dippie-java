package com.dippie.dto.quiz;

// một lựa chọn hiển thị cho bé (A, B, C hoặc D) đã trộn ngẫu nhiên từ server
public record QuizOptionView(String key, String text) {
}
