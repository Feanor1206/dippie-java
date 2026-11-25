package com.dippie.assistant.search;

import com.dippie.catalog.importing.CatalogIndexPublisher;
import com.dippie.catalog.importing.IndexSyncOutcome;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class LocalIndexPublisher implements CatalogIndexPublisher {

    private final LocalCatalogSearch search;

    LocalIndexPublisher(LocalCatalogSearch search) {
        this.search = search;
    }

    @Override
    public IndexSyncOutcome publish(long generation, String snapshotId, JsonNode catalog, boolean forceRebuild) {
        return search.accept(generation, snapshotId, catalog, forceRebuild);
    }
}
