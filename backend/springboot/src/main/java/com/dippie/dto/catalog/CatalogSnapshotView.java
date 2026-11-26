package com.dippie.dto.catalog;

import com.dippie.model.catalog.CatalogSnapshot;

import java.time.LocalDateTime;

// view trạng thái một bản snapshot catalog cho trang admin
public record CatalogSnapshotView(
        String id, long snapshotVersion, String contentHash, int totalItems, String status, Integer retryCount,
        String errorMessage, LocalDateTime createdAt, LocalDateTime completedAt, LocalDateTime updatedAt
) {

    public static CatalogSnapshotView from(CatalogSnapshot entity) {
        return new CatalogSnapshotView(entity.getId(), entity.getSnapshotVersion(), entity.getContentHash(),
                entity.getTotalItems(), entity.getStatus(), entity.getRetryCount(), entity.getErrorMessage(),
                entity.getCreatedAt(), entity.getCompletedAt(), entity.getUpdatedAt());
    }
}
