package com.dippie.assistant.search;

public record SearchEvidence(String documentId, String text, Long startMs, Long endMs, String itemType) {
}
