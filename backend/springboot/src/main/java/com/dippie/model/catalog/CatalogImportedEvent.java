package com.dippie.model.catalog;

import java.time.LocalDateTime;

// Sự kiện Spring (mẫu Observer) bắn ra sau khi import gói catalog xong, tách luồng import khỏi mấy bên nghe phía sau như làm mới cache, dựng lại index tìm kiếm, ghi audit và seed lịch sử demo.
public record CatalogImportedEvent(
        String packageId,
        int revision,
        long snapshotVersion,
        String snapshotId,
        String importedBy,
        LocalDateTime importedAt
) {
}
