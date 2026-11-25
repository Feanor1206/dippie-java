package com.dippie.catalog.importing;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record CatalogImportResult(boolean success, String importId, String packageId, int revision,
        long snapshotVersion, String contentHash, LocalDateTime importedAt, Map<String, Integer> counts,
        String indexStatus, String message) {

    public CatalogImportResult {
        counts = counts == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }
}
