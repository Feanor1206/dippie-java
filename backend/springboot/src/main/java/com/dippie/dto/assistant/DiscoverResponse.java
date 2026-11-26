package com.dippie.dto.assistant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

// JSON trả về cho API khám phá, DiscoverService dựng sẵn rồi controller chỉ việc đẩy ra
public record DiscoverResponse(Intent structuredIntent, String explanation, List<ObjectNode> results, List<String> followUps,
        PipelineStages pipelineStages) {

    // ý định tìm kiếm có cấu trúc gửi kèm trong phản hồi
    public record Intent(@JsonProperty("intent_type") String intentType, @JsonProperty("semantic_query") String semanticQuery) {
    }

    // báo tên bộ truy xuất với việc index cục bộ đã sẵn sàng chưa
    public record PipelineStages(String retrieval, boolean indexReady) {
    }
}
