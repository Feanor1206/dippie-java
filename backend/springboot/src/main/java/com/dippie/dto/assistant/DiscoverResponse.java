package com.dippie.dto.assistant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

// Phản hồi JSON của API khám phá, gom ý định tìm kiếm, danh sách phim, gợi ý câu hỏi kế tiếp và trạng thái chỉ mục.
// DiscoverService dựng record này, controller chỉ trả nó về cho giao diện.
public record DiscoverResponse(Intent structuredIntent, String explanation, List<ObjectNode> results, List<String> followUps,
        PipelineStages pipelineStages) {

    // Mô tả ý định khám phá có cấu trúc đi kèm phản hồi.
    public record Intent(@JsonProperty("intent_type") String intentType, @JsonProperty("semantic_query") String semanticQuery) {
    }

    // Báo tên bộ truy xuất và việc chỉ mục cục bộ đã sẵn sàng hay chưa.
    public record PipelineStages(String retrieval, boolean indexReady) {
    }
}
