package com.dippie.service.catalog;

import com.dippie.exception.CatalogInputException;
import com.dippie.model.catalog.ContentHash;

import java.util.List;

// DTO kết quả kiểm tra gói danh mục, giữ số liệu dự kiến thay đổi hoặc lỗi đầu tiên đụng phải
public record CatalogValidation(boolean valid, String packageId, Integer revision, String contentHash,
        int toCreate, int toUpdate, int unchanged, int conflicts, List<FieldError> errors) {

    public CatalogValidation {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    // dựng kết quả hợp lệ kèm mã gói, mã băm nội dung với bộ đếm sai khác
    public static CatalogValidation valid(CatalogPackageValidator.PackageIdentity identity, ContentHash hash,
            CatalogDiff.Counts counts) {
        return new CatalogValidation(true, identity.id(), identity.revision(), hash.value(),
                counts.toCreate(), counts.toUpdate(), counts.unchanged(), counts.conflicts(), List.of());
    }

    // bọc CatalogInputException thành kết quả lỗi mang mã INVALID_FIELD
    public static CatalogValidation invalid(CatalogInputException error) {
        return new CatalogValidation(false, null, null, null, 0, 0, 0, 0,
                List.of(new FieldError(error.path(), "INVALID_FIELD", error.getMessage())));
    }

    // chi tiết 1 field lỗi gồm đường dẫn JSON, mã lỗi với câu báo tiếng Việt
    public record FieldError(String path, String code, String message) {
    }
}
