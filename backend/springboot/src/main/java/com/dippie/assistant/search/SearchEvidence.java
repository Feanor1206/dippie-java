package com.dippie.assistant.search;

// Đoạn bằng chứng trích từ cảnh, phân đoạn hoặc cụm lời thoại kèm mốc thời gian để client tua đúng chỗ.
public record SearchEvidence(String documentId, String text, Long startMs, Long endMs, String itemType, String timeline,
        String mediaAssetId, Long sourceStartMs, Long sourceEndMs) {

    public SearchEvidence(String documentId, String text, Long startMs, Long endMs, String itemType) {
        this(documentId, text, startMs, endMs, itemType, null, null, null, null);
    }
}
