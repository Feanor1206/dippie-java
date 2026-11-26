package com.dippie.dto.catalog;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// DTO bất biến trả về sau khi nhập gói danh mục, mang mã snapshot, số lượng từng bảng và trạng thái đồng bộ chỉ mục.
public record CatalogImportResult(boolean success, String importId, String packageId, int revision,
        long snapshotVersion, String contentHash, LocalDateTime importedAt, Map<String, Integer> counts,
        String indexStatus, String message) {

    public CatalogImportResult {
        counts = counts == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }
}
