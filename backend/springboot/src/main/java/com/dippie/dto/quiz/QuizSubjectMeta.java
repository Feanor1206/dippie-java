package com.dippie.dto.quiz;

import com.dippie.model.quiz.QuizSubject;

// mô tả từng môn học cho bộ lọc và form thêm câu hỏi
public record QuizSubjectMeta(String code, String label) {

    public static QuizSubjectMeta from(QuizSubject subject) {
        return new QuizSubjectMeta(subject.name(), subject.label());
    }
}
