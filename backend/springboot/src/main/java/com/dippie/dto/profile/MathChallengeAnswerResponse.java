package com.dippie.dto.profile;

// kết quả chấm đáp án bài toán kèm số phút thưởng vừa cộng và trạng thái thời gian mới nhất
public record MathChallengeAnswerResponse(
        boolean correct,
        int earnedSeconds,
        int earnedMinutes,
        boolean bonusCapReached,
        ScreenTimeStatusView status
) {
}
