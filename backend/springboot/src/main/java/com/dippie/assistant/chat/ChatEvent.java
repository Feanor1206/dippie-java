package com.dippie.assistant.chat;

import java.util.Map;

// Sealed interface Java 21 gom 4 loại sự kiện SSE để LocalChatController switch không sót case.
public sealed interface ChatEvent permits ChatEvent.Metadata, ChatEvent.Token, ChatEvent.Done, ChatEvent.Error {

    String type();

    Object data();

    // Đẩy trước danh sách phim gợi ý và ngữ cảnh trước khi bắt đầu phát từng đoạn chữ.
    record Metadata(ChatMetadata payload) implements ChatEvent {
        @Override public String type() { return "metadata"; }
        @Override public Object data() { return payload; }
    }

    // Từng đoạn chữ ngắn bắn dần về frontend theo kiểu gõ trực tiếp.
    record Token(String text) implements ChatEvent {
        @Override public String type() { return "token"; }
        @Override public Object data() { return Map.of("text", text); }
    }

    // Khép lại luồng SSE kèm toàn văn câu trả lời và trạng thái pipeline cuối cùng.
    record Done(ChatDonePayload payload) implements ChatEvent {
        @Override public String type() { return "done"; }
        @Override public Object data() { return payload; }
    }

    // Gửi thông báo lỗi ngắn khi quá trình tìm kiếm gặp sự cố.
    record Error(String text) implements ChatEvent {
        @Override public String type() { return "error"; }
        @Override public Object data() { return Map.of("text", text); }
    }
}
