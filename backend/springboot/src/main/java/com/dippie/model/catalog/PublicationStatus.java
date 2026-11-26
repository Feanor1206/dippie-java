package com.dippie.model.catalog;

import com.dippie.common.json.JsonText;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;

// trạng thái xuất bản catalog (ready, draft, archived), thiếu hay lỗi thì kéo hết về DRAFT kẻo lộ tập chưa duyệt
public enum PublicationStatus {
    READY("ready"),
    DRAFT("draft"),
    ARCHIVED("archived");

    public static final String READY_VALUE = "ready";
    public static final String DRAFT_VALUE = "draft";
    public static final String ARCHIVED_VALUE = "archived";

    private final String value;

    PublicationStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    // đọc status trong recordJson, nhánh READY check thêm chuỗi gốc có chữ "ready" kẻo bị lách bằng unicode escape
    public static PublicationStatus of(String recordJson) {
        String status = JsonText.parse(recordJson)
                .filter(JsonNode::isObject)
                .map(record -> record.path("status").asText().toLowerCase(Locale.ROOT))
                .orElse("");

        return switch (status) {
            case READY_VALUE -> recordJson.toLowerCase(Locale.ROOT).contains(READY_VALUE) ? READY : DRAFT;
            case ARCHIVED_VALUE -> ARCHIVED;
            default -> DRAFT;
        };
    }

    // chỉ READY mới cho hiện ra phía người xem
    public boolean isPublished() {
        return this == READY;
    }
}
