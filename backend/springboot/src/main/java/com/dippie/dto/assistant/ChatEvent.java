package com.dippie.dto.assistant;

import java.util.Map;

// sealed interface Java 21 gom 4 kiểu sự kiện SSE để LocalChatController switch không sót case
public sealed interface ChatEvent permits ChatEvent.Metadata, ChatEvent.Token, ChatEvent.Done, ChatEvent.Error {

    String type();

    Object data();

    // bắn trước list phim gợi ý với ngữ cảnh trước khi stream từng chữ
    record Metadata(ChatMetadata payload) implements ChatEvent {
        @Override public String type() { return "metadata"; }
        @Override public Object data() { return payload; }
    }

    // từng mẩu chữ bắn dần về frontend kiểu gõ trực tiếp
    record Token(String text) implements ChatEvent {
        @Override public String type() { return "token"; }
        @Override public Object data() { return Map.of("text", text); }
    }

    // chốt luồng SSE kèm nguyên văn câu trả lời với trạng thái pipeline cuối
    record Done(ChatDonePayload payload) implements ChatEvent {
        @Override public String type() { return "done"; }
        @Override public Object data() { return payload; }
    }

    // gửi câu báo lỗi ngắn khi tìm kiếm bị trục trặc
    record Error(String text) implements ChatEvent {
        @Override public String type() { return "error"; }
        @Override public Object data() { return Map.of("text", text); }
    }
}
