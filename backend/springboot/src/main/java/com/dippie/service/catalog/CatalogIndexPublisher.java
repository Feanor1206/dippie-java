package com.dippie.service.catalog;

import com.dippie.model.catalog.IndexSyncOutcome;

import com.fasterxml.jackson.databind.JsonNode;

// cổng trừu tượng đẩy bản xuất danh mục sang bộ lập chỉ mục, tách luồng nhập khỏi cách cài chỉ mục phía dưới
public interface CatalogIndexPublisher {

    // gửi JSON danh mục kèm thế hệ với mã snapshot sang bộ chỉ mục, đặt forceRebuild = true lúc admin bấm đồng bộ lại
    IndexSyncOutcome publish(long generation, String snapshotId, JsonNode catalog, boolean forceRebuild);
}
