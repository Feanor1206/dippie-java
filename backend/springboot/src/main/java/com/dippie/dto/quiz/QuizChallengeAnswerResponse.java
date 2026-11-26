package com.dippie.dto.quiz;

import com.dippie.dto.profile.ScreenTimeStatusView;

// kết quả chấm trắc nghiệm thưởng giờ xem kèm đáp án đúng và giải thích ngắn
public record QuizChallengeAnswerResponse(
        boolean correct,
        String correctOption,
        String correctText,
        String explanation,
        int earnedSeconds,
        int earnedMinutes,
        boolean bonusCapReached,
        ScreenTimeStatusView status
) {
}
