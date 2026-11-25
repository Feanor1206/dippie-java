package com.dippie.catalog.importing;

import java.util.List;

public record CatalogValidation(boolean valid, String packageId, Integer revision, String contentHash,
        int toCreate, int toUpdate, int unchanged, int conflicts, List<FieldError> errors) {

    static CatalogValidation valid(CatalogPackageValidator.PackageIdentity identity, ContentHash hash,
            CatalogDiff.Counts counts) {
        return new CatalogValidation(true, identity.id(), identity.revision(), hash.value(),
                counts.toCreate(), counts.toUpdate(), counts.unchanged(), counts.conflicts(), List.of());
    }

    static CatalogValidation invalid(CatalogInputException error) {
        return new CatalogValidation(false, null, null, null, 0, 0, 0, 0,
                List.of(new FieldError(error.path(), "INVALID_FIELD", error.getMessage())));
    }

    public record FieldError(String path, String code, String message) {
    }
}
