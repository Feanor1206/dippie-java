package com.dippie.catalog.domain;

import com.dippie.common.json.JsonText;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;

// Trạng thái xuất bản của bản ghi catalog (ready, draft, archived); dữ liệu lỗi hoặc thiếu luôn kéo về DRAFT để khỏi lộ nhầm tập chưa duyệt.
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

    // Parse trường status trong recordJson; nhánh READY kiểm tra thêm raw text có chứa chữ "ready" để chặn trường hợp chuỗi bị escape unicode mà không ghi rõ ready.
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

    // Chỉ trạng thái READY mới được hiển thị ra cho người xem.
    public boolean isPublished() {
        return this == READY;
    }
}
