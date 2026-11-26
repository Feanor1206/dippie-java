package com.dippie.model.assistant;

import java.util.List;

// một lượt chat trong lịch sử, giữ thêm itemIds với documentIds lượt trước để hiểu câu hỏi nối tiếp
public record ChatHistoryMessage(String role, String content, List<String> itemIds, List<String> documentIds) {

    public ChatHistoryMessage {
        itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
        documentIds = documentIds == null ? List.of() : List.copyOf(documentIds);
    }

    public ChatHistoryMessage(String role, String content) {
        this(role, content, List.of(), List.of());
    }
}
