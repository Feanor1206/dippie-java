package com.dippie.assistant.chat;

import com.dippie.common.json.JsonText;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

final class ChatRequestValidator {

    private static final String MISSING_QUERY_DETAIL = "Thiếu trường query hoặc messages.";
    private static final String INVALID_JSON_DETAIL = "Dữ liệu JSON không hợp lệ.";
    private static final String INVALID_MESSAGE_DETAIL =
            "Each message must have a user or assistant role and text of at most 4000 characters";
    private static final int MAX_TEXT_LENGTH = 4000;
    private static final int MAX_MESSAGES = 40;
    private static final int MAX_SESSION_ID_LENGTH = 128;
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Set<String> ALLOWED_FIELDS = Set.of("query", "message", "messages", "sessionId");

    private final ObjectMapper objectMapper;

    ChatRequestValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    ValidatedChatRequest parseAndValidate(byte[] rawBody) {
        JsonNode root = parseJsonObject(rawBody);
        rejectUnknownFields(root);

        String query = parseQueryField(root);
        validateSessionId(root);
        List<ChatHistoryMessage> messages = parseMessages(root);

        String effectiveQuery = query == null ? "" : query;
        if (effectiveQuery.isBlank() && !messages.isEmpty()) {
            effectiveQuery = messages.getLast().content();
        }
        if (effectiveQuery.isBlank()) {
            throw new ChatValidationException(HttpStatus.BAD_REQUEST, MISSING_QUERY_DETAIL);
        }
        return new ValidatedChatRequest(effectiveQuery.trim(), messages);
    }

    private JsonNode parseJsonObject(byte[] rawBody) {
        if (rawBody == null || rawBody.length == 0) {
            throw new ChatValidationException(HttpStatus.BAD_REQUEST, INVALID_JSON_DETAIL);
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            if (root == null || !root.isObject()) {
                throw new ChatValidationException(HttpStatus.UNPROCESSABLE_ENTITY, INVALID_JSON_DETAIL);
            }
            return root;
        } catch (IOException exception) {
            throw new ChatValidationException(HttpStatus.BAD_REQUEST, INVALID_JSON_DETAIL);
        }
    }

    private void rejectUnknownFields(JsonNode root) {
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String field = names.next();
            if (!ALLOWED_FIELDS.contains(field)) {
                throw new ChatValidationException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Extra inputs are not permitted: " + field
                );
            }
        }
    }

    private String parseQueryField(JsonNode root) {
        JsonNode queryNode = root.has("query") ? root.get("query") : root.get("message");
        if (root.has("query") && root.has("message")) {
            validateOptionalQueryNode(root.get("message"));
        }
        return validateOptionalQueryNode(queryNode);
    }

    private String validateOptionalQueryNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new ChatValidationException(HttpStatus.UNPROCESSABLE_ENTITY, "query must be a string");
        }
        String value = node.asText();
        if (value.length() > MAX_TEXT_LENGTH) {
            throw new ChatValidationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "query must be at most " + MAX_TEXT_LENGTH + " characters"
            );
        }
        return value;
    }

    private void validateSessionId(JsonNode root) {
        JsonNode node = root.get("sessionId");
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isTextual()) {
            throw new ChatValidationException(HttpStatus.UNPROCESSABLE_ENTITY, "sessionId must be a string");
        }
        String value = node.asText();
        if (value.isEmpty() || value.length() > MAX_SESSION_ID_LENGTH || !SESSION_ID_PATTERN.matcher(value).matches()) {
            throw new ChatValidationException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid sessionId");
        }
    }

    private List<ChatHistoryMessage> parseMessages(JsonNode root) {
        JsonNode node = root.get("messages");
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray() || node.size() > MAX_MESSAGES) {
            throw new ChatValidationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "messages must be a list of at most " + MAX_MESSAGES + " items"
            );
        }
        List<ChatHistoryMessage> normalized = new ArrayList<>(node.size());
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                throw new ChatValidationException(HttpStatus.UNPROCESSABLE_ENTITY, INVALID_MESSAGE_DETAIL);
            }
            JsonNode roleNode = item.get("role");
            if (roleNode == null || !roleNode.isTextual()) {
                throw new ChatValidationException(HttpStatus.UNPROCESSABLE_ENTITY, INVALID_MESSAGE_DETAIL);
            }
            String role = roleNode.asText();
            if (!"user".equals(role) && !"assistant".equals(role)) {
                throw new ChatValidationException(HttpStatus.UNPROCESSABLE_ENTITY, INVALID_MESSAGE_DETAIL);
            }
            JsonNode contentNode = extractContentNode(item);
            if (contentNode == null || !contentNode.isTextual()) {
                throw new ChatValidationException(HttpStatus.UNPROCESSABLE_ENTITY, INVALID_MESSAGE_DETAIL);
            }
            String content = contentNode.asText();
            if (content.isBlank() || content.length() > MAX_TEXT_LENGTH) {
                throw new ChatValidationException(HttpStatus.UNPROCESSABLE_ENTITY, INVALID_MESSAGE_DETAIL);
            }
            normalized.add(new ChatHistoryMessage(role, content));
        }
        return List.copyOf(normalized);
    }

    private JsonNode extractContentNode(JsonNode message) {
        JsonNode content = message.get("content");
        if (!JsonText.isTruthy(content)) {
            return message.get("text");
        }
        return content;
    }

    record ValidatedChatRequest(String query, List<ChatHistoryMessage> messages) {
    }

    static final class ChatValidationException extends RuntimeException {

        private final HttpStatus status;

        ChatValidationException(HttpStatus status, String detail) {
            super(detail);
            this.status = status;
        }

        HttpStatus status() {
            return status;
        }
    }
}
