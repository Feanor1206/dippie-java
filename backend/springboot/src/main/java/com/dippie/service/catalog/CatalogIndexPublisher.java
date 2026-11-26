package com.dippie.service.catalog;

import com.dippie.model.catalog.IndexSyncOutcome;

import com.fasterxml.jackson.databind.JsonNode;

// Cổng trừu tượng đẩy bản xuất danh mục sang bộ chỉ mục tìm kiếm, tách hẳn luồng nhập khỏi cách cài đặt chỉ mục phía dưới.
public interface CatalogIndexPublisher {

    // Gửi JSON danh mục kèm thế hệ và mã snapshot sang bộ lập chỉ mục; đặt forceRebuild = true khi admin yêu cầu đồng bộ lại.
    IndexSyncOutcome publish(long generation, String snapshotId, JsonNode catalog, boolean forceRebuild);
}
