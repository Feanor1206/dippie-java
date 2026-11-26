package com.dippie.dto.catalog;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// kết quả trả về sau khi import catalog, gồm mã snapshot, số bản ghi từng bảng với trạng thái index
public record CatalogImportResult(boolean success, String importId, String packageId, int revision,
        long snapshotVersion, String contentHash, LocalDateTime importedAt, Map<String, Integer> counts,
        String indexStatus, String message) {

    public CatalogImportResult {
        counts = counts == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }
}
