package com.dippie.model.assistant;

// Ghi lại tên bước truy xuất, bước sinh câu trả lời và cờ grounded cho sự kiện metadata/done.
public record ChatPipelineStages(String retrieval, String conversation, boolean grounded) {
}
