package com.dippie.dto.assistant;

// trạng thái trợ lý trả ra ở /api/assistant/status
public record AssistantStatus(boolean searchReady, Integer documents, String llmProvider) {
}
