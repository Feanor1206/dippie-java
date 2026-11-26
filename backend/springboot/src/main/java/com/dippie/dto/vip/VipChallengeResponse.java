package com.dippie.dto.vip;

import com.dippie.dto.quiz.QuizOptionView;

import java.time.LocalDateTime;
import java.util.List;

// câu hỏi trắc nghiệm mức khó hơn (lớp kế tiếp hoặc lớp cao nhất sẵn có) để bé mở khóa 1 tập phim VIP trong hôm nay
public record VipChallengeResponse(
        String token,
        String episodeId,
        String seriesId,
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
