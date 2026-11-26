package com.dippie.dto.vip;

import java.time.LocalDateTime;

// đề toán khó hơn 1 bậc (tối đa mức 4) kèm token ký HMAC để bé mở khóa 1 tập phim VIP trong hôm nay
public record VipChallengeResponse(
        String token,
        String episodeId,
        String seriesId,
        String question,
        int mathLevel,
        LocalDateTime expiresAt,
        long expiresInSeconds
) {
}
