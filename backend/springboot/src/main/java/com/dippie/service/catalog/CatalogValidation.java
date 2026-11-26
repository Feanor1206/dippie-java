package com.dippie.service.catalog;

import com.dippie.exception.CatalogInputException;
import com.dippie.model.catalog.ContentHash;

import java.util.List;

// DTO kết quả kiểm tra gói danh mục, chứa số liệu dự kiến thay đổi hoặc lỗi đầu tiên gặp phải.
public record CatalogValidation(boolean valid, String packageId, Integer revision, String contentHash,
        int toCreate, int toUpdate, int unchanged, int conflicts, List<FieldError> errors) {

    public CatalogValidation {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    // Dựng kết quả hợp lệ kèm mã gói, mã băm nội dung và bộ đếm sai khác.
    public static CatalogValidation valid(CatalogPackageValidator.PackageIdentity identity, ContentHash hash,
            CatalogDiff.Counts counts) {
        return new CatalogValidation(true, identity.id(), identity.revision(), hash.value(),
                counts.toCreate(), counts.toUpdate(), counts.unchanged(), counts.conflicts(), List.of());
    }

    // Gói ngoại lệ CatalogInputException thành kết quả lỗi với mã INVALID_FIELD.
    public static CatalogValidation invalid(CatalogInputException error) {
        return new CatalogValidation(false, null, null, null, 0, 0, 0, 0,
                List.of(new FieldError(error.path(), "INVALID_FIELD", error.getMessage())));
    }

    // Thông tin một trường bị lỗi gồm đường dẫn JSON, mã lỗi và câu báo lỗi tiếng Việt.
    public record FieldError(String path, String code, String message) {
    }
}
