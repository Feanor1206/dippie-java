package com.dippie.dto.play;

import com.dippie.model.play.RecapQuizType;

import java.time.LocalDateTime;
import java.util.List;

// bộ câu hỏi ôn tập sinh từ catalog kèm token mã hóa chứa đáp án đúng phía server
public record RecapQuizResponse(
        String token,
        String episodeId,
        String episodeTitle,
        String targetEpisodeId,
        RecapQuizType quizType,
        String prompt,
        List<RecapQuizItemView> items,
        LocalDateTime expiresAt,
        long expiresInSeconds
) {
}
