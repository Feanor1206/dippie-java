package com.dippie.assistant.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

// Phản hồi JSON của API khám phá, gom ý định tìm kiếm, danh sách phim, gợi ý câu hỏi kế tiếp và trạng thái chỉ mục.
record DiscoverResponse(Intent structuredIntent, String explanation, List<ObjectNode> results, List<String> followUps,
        PipelineStages pipelineStages) {

    // Dựng phản hồi hoàn chỉnh từ các Match trả về của LexicalIndex.
    static DiscoverResponse of(String query, List<LexicalIndex.Match> results, ObjectMapper mapper) {
        List<SearchHit> hits = results.stream().map(LexicalIndex.Match::hit).toList();
        return new DiscoverResponse(new Intent("DISCOVERY", query),
                results.isEmpty() ? "Chưa tìm thấy phim hay tập nào khớp yêu cầu của bạn." : "Đã tìm trong kho phim hiện có.",
                results.stream().map(match -> movieItem(match, mapper)).toList(),
                FollowUpSuggestions.build(hits), new PipelineStages("java-lexical", true));
    }

    // Gắn nhãn thể loại tiếng Việt và bổ sung mốc thời gian tệp nguồn vào evidence đầu tiên cho đúng schema client đang đọc.
    private static ObjectNode movieItem(LexicalIndex.Match match, ObjectMapper mapper) {
        SearchHit hit = match.hit();
        ObjectNode result = mapper.valueToTree(hit);
        result.set("genres", mapper.valueToTree(match.genreLabels()));
        ObjectNode evidence = (ObjectNode) result.path("evidence").get(0);
        evidence.put("sourceStartMs", hit.startMsSnake());
        evidence.put("sourceEndMs", hit.endMs());
        evidence.put("mediaAssetId", match.evidenceMediaAssetId());
        evidence.put("timeline", hit.episodeId() != null && hit.startMs() != null ? "episode" : "file");
        return result;
    }

    // Mô tả ý định khám phá có cấu trúc đi kèm phản hồi.
    record Intent(@JsonProperty("intent_type") String intentType, @JsonProperty("semantic_query") String semanticQuery) {
    }

    // Báo tên bộ truy xuất và việc chỉ mục cục bộ đã sẵn sàng hay chưa.
    record PipelineStages(String retrieval, boolean indexReady) {
    }
}
