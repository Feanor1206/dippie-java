package com.dippie.catalog.domain;

import java.time.LocalDateTime;

public record CatalogSnapshotView(
        String id,
        long snapshotVersion,
        String contentHash,
        int totalItems,
        String status,
        Integer retryCount,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        LocalDateTime updatedAt
) {

    public static CatalogSnapshotView from(CatalogSnapshot entity) {
        return new CatalogSnapshotView(
                entity.getId(),
                entity.getSnapshotVersion(),
                entity.getContentHash(),
                entity.getTotalItems(),
                entity.getStatus(),
                entity.getRetryCount(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getCompletedAt(),
                entity.getUpdatedAt());
    }
}
