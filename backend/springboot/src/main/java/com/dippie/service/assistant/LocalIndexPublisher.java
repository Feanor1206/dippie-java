package com.dippie.service.assistant;

import com.dippie.model.catalog.IndexSyncOutcome;
import com.dippie.service.catalog.CatalogIndexPublisher;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

// Lớp Adapter nối CatalogIndexPublisher từ gói import sang LocalCatalogSearch để luồng nhập dữ liệu không dính chặt vào chi tiết tìm kiếm.
@Component
public class LocalIndexPublisher implements CatalogIndexPublisher {

    private final LocalCatalogSearch search;

    public LocalIndexPublisher(LocalCatalogSearch search) {
        this.search = search;
    }

    @Override
    public IndexSyncOutcome publish(long generation, String snapshotId, JsonNode catalog, boolean forceRebuild) {
        return search.accept(generation, snapshotId, catalog, forceRebuild);
    }
}
