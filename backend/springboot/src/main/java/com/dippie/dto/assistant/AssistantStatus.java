package com.dippie.dto.assistant;

// Payload trạng thái trả về cho client ở /api/assistant/status.
public record AssistantStatus(boolean searchReady, Integer documents, String llmProvider) {
}
