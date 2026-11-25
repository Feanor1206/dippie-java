package com.dippie.common.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

// Bộ hàm đọc chuỗi JSON thô bằng Jackson. Mình bật FAIL_ON_TRAILING_TOKENS để chuỗi hỏng kiểu {"a":1} rác bị loại ngay thay vì bỏ qua.
public final class JsonText {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private JsonText() {
    }

    // Trả rỗng nếu chuỗi trống hoặc sai cú pháp JSON.
    public static Optional<JsonNode> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(MAPPER.readTree(text));
        } catch (JsonProcessingException ignored) {
            return Optional.empty();
        }
    }

    // Lấy các phần tử dạng chuỗi trong một mảng JSON, bỏ qua phần tử khác kiểu.
    public static List<String> stringList(String text) {
        return parse(text)
                .filter(JsonNode::isArray)
                .map(array -> array.valueStream()
                        .filter(JsonNode::isTextual)
                        .map(JsonNode::textValue)
                        .toList())
                .orElseGet(List::of);
    }

    // Đánh giá đúng/sai của một JsonNode theo thói quen truthy bên JavaScript (rỗng, null, 0 hay mảng trống đều là false).
    public static boolean isTruthy(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return false;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            return !node.textValue().isEmpty();
        }
        if (node.isNumber()) {
            return node.doubleValue() != 0.0;
        }
        return !node.isEmpty();
    }
}
