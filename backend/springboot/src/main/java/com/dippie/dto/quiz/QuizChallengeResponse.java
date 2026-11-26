package com.dippie.dto.quiz;

import java.time.LocalDateTime;
import java.util.List;

// câu hỏi trắc nghiệm 4 lựa chọn kèm token mã hóa dùng 1 lần để bé trả lời kiếm thêm phút xem
public record QuizChallengeResponse(
        String token,
        String question,
        List<QuizOptionView> options,
        String subject,
        String subjectLabel,
        String grade,
        String gradeLabel,
        String setName,
        int rewardMinutes,
        LocalDateTime expiresAt,
        long expiresInSeconds,
        long bonusRemainingSeconds
) {
}
