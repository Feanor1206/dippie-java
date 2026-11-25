package com.dippie.assistant.chat;

public record ChatPipelineStages(String retrieval, String conversation, boolean grounded) {
}
