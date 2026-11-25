package com.dippie.assistant.chat;

import com.dippie.assistant.search.SearchHit;
import java.util.List;

// Gói dữ liệu kết thúc của sự kiện SSE "done", gửi kèm toàn văn trả lời, phim gợi ý và các gợi ý hỏi tiếp.
public record ChatDonePayload(
        String text,
        List<SearchHit> recommendedMovies,
        List<String> followUps,
        ChatIntent structuredIntent,
        ChatPipelineStages pipelineStages
) {

    public ChatDonePayload {
        recommendedMovies = recommendedMovies == null ? List.of() : List.copyOf(recommendedMovies);
        followUps = followUps == null ? List.of() : List.copyOf(followUps);
    }
}
