package com.dippie.dto.catalog;

import com.dippie.model.catalog.CatalogMediaAsset;

import java.time.LocalDateTime;

// Bản ghi trả về cho một media asset, gắn thêm cờ available báo file vật lý ngoài đĩa đã sẵn sàng phát chưa.
public record CatalogMediaAssetView(
        String id, String contentType, String role, String editionLabel, String spokenLanguagesJson,
        String embeddedSubtitleLanguagesJson, String storageProvider, String storageKey, String relativePath,
        String driveFileId, String driveFolderId, Long sizeBytes, String sha256, Long durationMs, String technicalJson,
        String coverageJson, String relatedAssetId, String accessPolicy, boolean enabled, String recordJson,
        String rawJson, int revision, Long optLockVersion, LocalDateTime updatedAt, boolean available
) {

    public static CatalogMediaAssetView from(CatalogMediaAsset entity, boolean available) {
        return new CatalogMediaAssetView(entity.getId(), entity.getContentType(), entity.getRole(),
                entity.getEditionLabel(), entity.getSpokenLanguagesJson(), entity.getEmbeddedSubtitleLanguagesJson(),
                entity.getStorageProvider(), entity.getStorageKey(), entity.getRelativePath(), entity.getDriveFileId(),
                entity.getDriveFolderId(), entity.getSizeBytes(), entity.getSha256(), entity.getDurationMs(),
                entity.getTechnicalJson(), entity.getCoverageJson(), entity.getRelatedAssetId(),
                entity.getAccessPolicy(), entity.isEnabled(), entity.getRecordJson(), entity.getRawJson(),
                entity.getRevision(), entity.getOptLockVersion(), entity.getUpdatedAt(), available);
    }
}
