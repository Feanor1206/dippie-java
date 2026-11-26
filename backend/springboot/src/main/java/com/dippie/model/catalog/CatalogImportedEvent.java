package com.dippie.model.catalog;

import java.time.LocalDateTime;

// sự kiện Spring kiểu Observer bắn ra ngay sau khi import catalog xong
// tách luồng import khỏi mấy bên nghe phía sau như làm mới cache, dựng index, ghi audit với seed lịch sử demo
public record CatalogImportedEvent(
        String packageId,
        int revision,
        long snapshotVersion,
        String snapshotId,
        String importedBy,
        LocalDateTime importedAt
) {
}
