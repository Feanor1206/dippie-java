package com.dippie.model.assistant;

import java.util.List;

// Một lượt tin nhắn trong lịch sử trò chuyện, giữ thêm itemIds và documentIds của lượt trước để giải câu hỏi nối tiếp.
public record ChatHistoryMessage(String role, String content, List<String> itemIds, List<String> documentIds) {

    public ChatHistoryMessage {
        itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
        documentIds = documentIds == null ? List.of() : List.copyOf(documentIds);
    }

    public ChatHistoryMessage(String role, String content) {
        this(role, content, List.of(), List.of());
    }
}
