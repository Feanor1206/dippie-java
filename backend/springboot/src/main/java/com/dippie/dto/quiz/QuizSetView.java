package com.dippie.dto.quiz;

import com.dippie.model.quiz.QuizSet;

import java.time.LocalDateTime;

// thông tin 1 bộ câu hỏi (có sẵn hoặc của phụ huynh) kèm số câu hiện có
public record QuizSetView(
        Long id,
        String name,
        String description,
        String defaultSubject,
        String defaultSubjectLabel,
        String defaultGrade,
        String defaultGradeLabel,
        boolean builtIn,
        boolean dynamicGenerator,
        boolean active,
        long questionCount,
        int maxQuestionsPerSet,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static QuizSetView from(QuizSet set, long questionCount) {
        return new QuizSetView(
                set.getId(),
                set.getName(),
                set.getDescription(),
                set.getDefaultSubject().name(),
                set.getDefaultSubject().label(),
                set.getDefaultGrade() == null ? null : set.getDefaultGrade().name(),
                set.getDefaultGrade() == null ? null : set.getDefaultGrade().label(),
                set.isBuiltIn(),
                set.isDynamicGenerator(),
                set.isActive(),
                questionCount,
                QuizSet.MAX_QUESTIONS_PER_SET,
                set.getCreatedAt(),
                set.getUpdatedAt()
        );
    }
}
