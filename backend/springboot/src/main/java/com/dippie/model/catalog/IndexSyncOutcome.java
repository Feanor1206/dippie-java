package com.dippie.model.catalog;

// kết quả trạng thái sau khi đẩy một snapshot catalog sang index tìm kiếm
public enum IndexSyncOutcome {
    READY, PENDING, BUILDING, SYNC_FAILED
}
