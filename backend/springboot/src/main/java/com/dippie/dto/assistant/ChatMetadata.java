package com.dippie.dto.assistant;

import com.dippie.model.assistant.ChatIntent;
import com.dippie.model.assistant.ChatPipelineStages;
import com.dippie.model.assistant.SearchHit;

import java.util.List;

// dữ liệu mở màn gửi trong SSE "metadata" trước lúc stream từng chữ
public record ChatMetadata(
        List<SearchHit> recommendedMovies,
        List<String> followUps,
        ChatIntent structuredIntent,
        ChatPipelineStages pipelineStages
) {

    public ChatMetadata {
        recommendedMovies = recommendedMovies == null ? List.of() : List.copyOf(recommendedMovies);
        followUps = followUps == null ? List.of() : List.copyOf(followUps);
    }
}
