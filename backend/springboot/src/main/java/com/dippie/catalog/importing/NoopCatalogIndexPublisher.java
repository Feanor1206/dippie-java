package com.dippie.catalog.importing;

import com.fasterxml.jackson.databind.JsonNode;

public final class NoopCatalogIndexPublisher implements CatalogIndexPublisher {

    @Override
    public IndexSyncOutcome publish(long generation, String snapshotId, JsonNode catalog, boolean forceRebuild) {
        return IndexSyncOutcome.SYNC_FAILED;
    }
}
