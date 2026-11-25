package com.dippie.catalog.importing;

// Trạng thái trả về khi đồng bộ một snapshot danh mục sang bộ chỉ mục tìm kiếm.
public enum IndexSyncOutcome {
    READY, PENDING, BUILDING, SYNC_FAILED
}
