package com.dippie.model.assistant;

// đoạn trích từ cảnh, phân đoạn hay lời thoại kèm mốc giờ để client tua tới đúng chỗ
public record SearchEvidence(String documentId, String text, Long startMs, Long endMs, String itemType, String timeline,
        String mediaAssetId, Long sourceStartMs, Long sourceEndMs) {

    public SearchEvidence(String documentId, String text, Long startMs, Long endMs, String itemType) {
        this(documentId, text, startMs, endMs, itemType, null, null, null, null);
    }
}
