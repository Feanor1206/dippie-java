package com.dippie.dto.quiz;

import com.dippie.model.quiz.QuizQuestion;

import java.time.LocalDateTime;

// chi tiết 1 câu hỏi trong bộ câu hỏi hiển thị cho phụ huynh xem hoặc sửa
public record QuizQuestionView(
        Long id,
        Long setId,
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
        String gradeLabel,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static QuizQuestionView from(QuizQuestion question) {
        return new QuizQuestionView(
                question.getId(),
                question.getSetId(),
                question.getQuestionText(),
                question.getOptionA(),
                question.getOptionB(),
                question.getOptionC(),
                question.getOptionD(),
                question.getCorrectOption().name(),
                question.getExplanation(),
                question.getSubject().name(),
                question.getSubject().label(),
                question.getGrade().name(),
                question.getGrade().label(),
                question.isActive(),
                question.getCreatedAt(),
                question.getUpdatedAt()
        );
    }
}
