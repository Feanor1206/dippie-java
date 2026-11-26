package com.dippie.dto.profile;

import java.time.LocalDateTime;

// câu hỏi toán phát cho bé kèm token đã mã hóa ký HMAC, tuyệt đối không trả đáp án về client
public record MathChallengeResponse(
        String token,
        String question,
        int mathLevel,
        int rewardMinutes,
        LocalDateTime expiresAt,
        long expiresInSeconds,
        long bonusRemainingSeconds
) {
}
