package com.dippie.assistant.chat;

import com.dippie.assistant.search.SearchHit;
import java.util.List;

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
