package com.dippie.model.assistant;

// ghi tên bước truy xuất, bước sinh câu trả lời với cờ grounded cho sự kiện metadata/done
public record ChatPipelineStages(String retrieval, String conversation, boolean grounded) {
}
