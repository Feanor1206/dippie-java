package com.dippie.dto.quiz;

import com.dippie.model.quiz.QuizGrade;

// mô tả từng khối lớp hiển thị trong form chọn lớp của phụ huynh
public record QuizGradeMeta(String code, String label, String description) {

    public static QuizGradeMeta from(QuizGrade grade) {
        return new QuizGradeMeta(grade.name(), grade.label(), grade.description());
    }
}
