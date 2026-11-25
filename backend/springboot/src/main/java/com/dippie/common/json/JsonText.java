package com.dippie.common.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

public final class JsonText {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private JsonText() {
    }

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

    public static List<String> stringList(String text) {
        return parse(text)
                .filter(JsonNode::isArray)
                .map(array -> StreamSupport.stream(array.spliterator(), false)
                        .filter(JsonNode::isTextual)
                        .map(JsonNode::textValue)
                        .toList())
                .orElseGet(List::of);
    }
}
