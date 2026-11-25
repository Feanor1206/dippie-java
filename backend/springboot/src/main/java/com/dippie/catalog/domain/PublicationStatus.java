package com.dippie.catalog.domain;

import com.dippie.common.json.JsonText;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;

public enum PublicationStatus {
    READY, DRAFT, ARCHIVED;

    public static PublicationStatus of(String recordJson) {
        String status = JsonText.parse(recordJson)
                .filter(JsonNode::isObject)
                .map(record -> record.path("status").asText().toLowerCase(Locale.ROOT))
                .orElse("");

        return switch (status) {
            case "ready" -> recordJson.toLowerCase(Locale.ROOT).contains("ready") ? READY : DRAFT;
            case "archived" -> ARCHIVED;
            default -> DRAFT;
        };
    }

    public boolean isPublished() {
        return this == READY;
    }
}
