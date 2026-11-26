package com.dippie.dto.catalog;

import com.dippie.model.catalog.CatalogPackage;

import java.time.LocalDateTime;

// view gói catalog đã import cho trang admin, dựng từ entity qua from
public record CatalogPackageView(
        String id, String version, int revision, String defaultLanguage, String importMode, String referenceScope,
        String description, String contentHash, String status, String errorMessage, Long optLockVersion,
        LocalDateTime createdAt, LocalDateTime importedAt
) {

    public static CatalogPackageView from(CatalogPackage entity) {
        return new CatalogPackageView(entity.getId(), entity.getVersion(), entity.getRevision(),
                entity.getDefaultLanguage(), entity.getImportMode(), entity.getReferenceScope(),
                entity.getDescription(), entity.getContentHash(), entity.getStatus(), entity.getErrorMessage(),
                entity.getOptLockVersion(), entity.getCreatedAt(), entity.getImportedAt());
    }
}
