package com.dippie.ai;

import com.dippie.catalog.importing.CatalogIndexPublisher;
import com.dippie.catalog.importing.IndexSyncOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HttpCatalogIndexPublisher implements CatalogIndexPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(HttpCatalogIndexPublisher.class);

    private final AiServiceClient client;
    private final ObjectMapper mapper;

    public HttpCatalogIndexPublisher(AiServiceClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public IndexSyncOutcome publish(long generation, String snapshotId, JsonNode catalog, boolean forceRebuild) {
        try {
            var request = mapper.createObjectNode().put("generation", generation)
                    .putNull("content_hash").put("force_rebuild", forceRebuild);
            request.set("catalog", catalog);
            return IndexSyncOutcome.fromAcknowledgment(client.syncIndex(request), generation);
        } catch (RuntimeException exception) {
            LOG.warn("Catalog index sync failed for generation {} ({})",
                    generation, exception.getClass().getSimpleName());
            return IndexSyncOutcome.SYNC_FAILED;
        }
    }
}
