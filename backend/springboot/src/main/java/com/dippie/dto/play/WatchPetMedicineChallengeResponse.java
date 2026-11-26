package com.dippie.dto.play;

import com.dippie.dto.quiz.QuizOptionView;

import java.time.LocalDateTime;
import java.util.List;

// câu hỏi trắc nghiệm ký HMAC để bé mua thuốc hồi sức cho Dieppie khi Dieppie mệt xỉu
public record WatchPetMedicineChallengeResponse(
        String token,
        String question,
        List<QuizOptionView> options,
        String subject,
        String subjectLabel,
        String grade,
        String gradeLabel,
        String setName,
        LocalDateTime expiresAt,
        long expiresInSeconds
) {
}
