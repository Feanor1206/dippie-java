package com.dippie.dto.play;

// kết quả kiểm tra câu hỏi ôn tập và trạng thái mở khóa tập kế tiếp
public record RecapQuizAnswerResponse(
        boolean correct,
        String episodeId,
        String targetEpisodeId,
        boolean unlocked
) {
}
