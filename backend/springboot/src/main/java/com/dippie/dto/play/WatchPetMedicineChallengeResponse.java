package com.dippie.dto.play;

import java.time.LocalDateTime;

// câu hỏi toán ký HMAC để bé mua thuốc hồi máu cho thú ảo khi thú mệt xỉu
public record WatchPetMedicineChallengeResponse(
        String token,
        String question,
        int mathLevel,
        LocalDateTime expiresAt,
        long expiresInSeconds
) {
}
