package com.dippie.catalog.importing;

import com.fasterxml.jackson.databind.JsonNode;

public interface CatalogIndexPublisher {

    IndexSyncOutcome publish(long generation, String snapshotId, JsonNode catalog, boolean forceRebuild);
}
