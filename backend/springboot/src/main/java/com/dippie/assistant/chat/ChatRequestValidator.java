package com.dippie.assistant.chat;

import com.dippie.common.error.DomainException;
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

// Giải mã và kiểm tra body JSON gửi lên /api/chat: chặn trường lạ, giới hạn độ dài chữ và chỉ nhận role user/assistant.
final class ChatRequestValidator {

    private static final String MISSING_QUERY_DETAIL = "Bạn chưa nhập câu hỏi cho CineBot.";
    private static final String INVALID_JSON_DETAIL = "Dữ liệu gửi lên không đúng định dạng.";
    private static final String INVALID_MESSAGE_DETAIL =
            "Mỗi tin nhắn trong lịch sử cần có vai trò user hoặc assistant và không dài quá 4000 ký tự.";
    private static final int MAX_TEXT_LENGTH = 4000;
    private static final int MAX_MESSAGES = 40;
    private static final int MAX_SESSION_ID_LENGTH = 128;
    private static final int MAX_REFERENCES = 100;
    private static final int MAX_REFERENCE_LENGTH = 300;
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Set<String> ALLOWED_FIELDS = Set.of("query", "message", "messages", "sessionId");

    private final ObjectMapper objectMapper;

    ChatRequestValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Nhận cả field query lẫn message; nếu cả hai đều trống thì lấy tin nhắn cuối trong messages làm câu hỏi hiệu lực.
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
                throw unprocessable(INVALID_JSON_DETAIL);
            }
            return root;
        } catch (IOException exception) {
            throw new ChatValidationException(HttpStatus.BAD_REQUEST, INVALID_JSON_DETAIL);
        }
    }

    private void rejectUnknownFields(JsonNode root) {
        for (Iterator<String> names = root.fieldNames(); names.hasNext();) {
            String field = names.next();
            if (!ALLOWED_FIELDS.contains(field)) {
                throw unprocessable("Yêu cầu có trường không được hỗ trợ: " + field);
            }
        }
    }

    private String parseQueryField(JsonNode root) {
        JsonNode queryNode = root.has("query") ? root.get("query") : root.get("message");
        if (root.has("query") && root.has("message")) {
            validateQueryNode(root.get("message"));
        }
        return validateQueryNode(queryNode);
    }

    private String validateQueryNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw unprocessable("Câu hỏi phải là dạng văn bản.");
        }
        String text = node.asText();
        if (text.length() > MAX_TEXT_LENGTH) {
            throw unprocessable("Câu hỏi không được vượt quá " + MAX_TEXT_LENGTH + " ký tự.");
        }
        return text;
    }

    private void validateSessionId(JsonNode root) {
        JsonNode sessionNode = root.get("sessionId");
        if (sessionNode == null || sessionNode.isNull()) {
            return;
        }
        if (!sessionNode.isTextual()) {
            throw unprocessable("Mã phiên phải là chuỗi ký tự.");
        }
        String sessionId = sessionNode.asText();
        if (sessionId.isEmpty() || sessionId.length() > MAX_SESSION_ID_LENGTH
                || !SESSION_ID_PATTERN.matcher(sessionId).matches()) {
            throw unprocessable("Mã phiên không hợp lệ.");
        }
    }

    // Chỉ tin nhắn vai trò assistant mới giữ itemIds và documentIds, còn role khác như system bị chặn luôn ở đây.
    private List<ChatHistoryMessage> parseMessages(JsonNode root) {
        JsonNode messagesNode = root.get("messages");
        if (messagesNode == null || messagesNode.isNull()) {
            return List.of();
        }
        if (!messagesNode.isArray() || messagesNode.size() > MAX_MESSAGES) {
            throw unprocessable("Lịch sử trò chuyện chỉ giữ tối đa " + MAX_MESSAGES + " tin nhắn.");
        }
        List<ChatHistoryMessage> normalized = new ArrayList<>(messagesNode.size());
        for (JsonNode messageNode : messagesNode) {
            JsonNode roleNode = messageNode == null || !messageNode.isObject() ? null : messageNode.get("role");
            String role = roleNode != null && roleNode.isTextual() ? roleNode.asText() : null;
            if (!"user".equals(role) && !"assistant".equals(role)) {
                throw unprocessable(INVALID_MESSAGE_DETAIL);
            }
            JsonNode contentNode = extractContentNode(messageNode);
            String content = contentNode != null && contentNode.isTextual() ? contentNode.asText() : null;
            if (content == null || content.isBlank() || content.length() > MAX_TEXT_LENGTH) {
                throw unprocessable(INVALID_MESSAGE_DETAIL);
            }
            normalized.add("assistant".equals(role)
                    ? new ChatHistoryMessage(role, content,
                            parseReferenceIds(messageNode.get("itemIds"), "itemIds"),
                            parseReferenceIds(messageNode.get("documentIds"), "documentIds"))
                    : new ChatHistoryMessage(role, content));
        }
        return List.copyOf(normalized);
    }

    private List<String> parseReferenceIds(JsonNode idsNode, String field) {
        if (idsNode == null || idsNode.isNull()) {
            return List.of();
        }
        if (!idsNode.isArray() || idsNode.size() > MAX_REFERENCES) {
            throw unprocessable("Danh sách " + field + " chỉ chứa tối đa " + MAX_REFERENCES + " mã.");
        }
        List<String> ids = new ArrayList<>(idsNode.size());
        for (JsonNode entry : idsNode) {
            if (!entry.isTextual() || entry.asText().isBlank() || entry.asText().length() > MAX_REFERENCE_LENGTH) {
                throw unprocessable("Mỗi mã trong " + field + " cần dài từ 1 đến " + MAX_REFERENCE_LENGTH + " ký tự.");
            }
            ids.add(entry.asText().strip());
        }
        return ids;
    }

    private static JsonNode extractContentNode(JsonNode message) {
        JsonNode content = message.get("content");
        return JsonText.isTruthy(content) ? content : message.get("text");
    }

    private static ChatValidationException unprocessable(String detail) {
        return new ChatValidationException(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }

    // Câu hỏi hiệu lực cùng danh sách lịch sử hội thoại sau khi đã qua bước kiểm tra.
    record ValidatedChatRequest(String query, List<ChatHistoryMessage> messages) {
    }

    // Kế thừa DomainException để báo lỗi dữ liệu đầu vào /api/chat về JSON {"detail": "..."}.
    static final class ChatValidationException extends DomainException {

        ChatValidationException(HttpStatus status, String detail) {
            super(status, detail);
        }
    }
}
