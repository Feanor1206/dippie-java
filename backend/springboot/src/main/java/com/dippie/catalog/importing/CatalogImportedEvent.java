package com.dippie.catalog.importing;

import java.time.LocalDateTime;

public record CatalogImportedEvent(
        String packageId,
        int revision,
        long snapshotVersion,
        String snapshotId,
        String importedBy,
        LocalDateTime importedAt
) {
}
