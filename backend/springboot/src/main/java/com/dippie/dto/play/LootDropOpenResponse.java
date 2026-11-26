package com.dippie.dto.play;

import com.dippie.dto.quiz.QuizOptionView;

import java.time.LocalDateTime;
import java.util.List;

// câu hỏi trắc nghiệm nhanh 10 giây khi bé bấm mở rương báu bay ngang
public record LootDropOpenResponse(
        String token,
        String question,
        List<QuizOptionView> options,
        String subject,
        String subjectLabel,
        String grade,
        String gradeLabel,
        String setName,
        int timerSeconds,
        LocalDateTime expiresAt,
        int combo,
        int baseRewardMinutes,
        int potentialComboBonusMinutes
) {
}
