package com.dippie.assistant.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

record DiscoverResponse(Intent structuredIntent, String explanation, List<ObjectNode> results, List<String> followUps,
        PipelineStages pipelineStages) {

    static DiscoverResponse of(String query, List<LexicalIndex.Match> results, ObjectMapper mapper) {
        List<SearchHit> hits = results.stream().map(LexicalIndex.Match::hit).toList();
        return new DiscoverResponse(new Intent("DISCOVERY", query),
                results.isEmpty() ? "Không tìm thấy nội dung phù hợp." : "Đã tìm trong catalog hiện tại.",
                results.stream().map(match -> movieItem(match, mapper)).toList(),
                FollowUpSuggestions.build(hits), new PipelineStages("java-lexical", true));
    }

    private static ObjectNode movieItem(LexicalIndex.Match match, ObjectMapper mapper) {
        SearchHit hit = match.hit();
        ObjectNode result = mapper.valueToTree(hit);
        ObjectNode evidence = (ObjectNode) result.path("evidence").get(0);
        evidence.put("sourceStartMs", hit.startMsSnake());
        evidence.put("sourceEndMs", hit.endMs());
        evidence.put("mediaAssetId", match.evidenceMediaAssetId());
        evidence.put("timeline", hit.episodeId() != null && hit.startMs() != null ? "episode" : "file");
        return result;
    }

    record Intent(@JsonProperty("intent_type") String intentType, @JsonProperty("semantic_query") String semanticQuery) {
    }

    record PipelineStages(String retrieval, boolean indexReady) {
    }
}
