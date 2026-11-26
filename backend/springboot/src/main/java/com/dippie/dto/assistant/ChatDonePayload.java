package com.dippie.dto.assistant;

import com.dippie.model.assistant.ChatIntent;
import com.dippie.model.assistant.ChatPipelineStages;
import com.dippie.model.assistant.SearchHit;

import java.util.List;

// gói chốt của sự kiện SSE "done", kèm đủ câu trả lời, phim gợi ý với câu hỏi tiếp
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
