package com.dippie.dto.assistant;

import com.dippie.model.assistant.ChatIntent;
import com.dippie.model.assistant.ChatPipelineStages;
import com.dippie.model.assistant.SearchHit;

import java.util.List;

// Khối thông tin đầu lượt chat gửi trong sự kiện SSE "metadata" trước khi bắt đầu phát từng đoạn chữ.
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
