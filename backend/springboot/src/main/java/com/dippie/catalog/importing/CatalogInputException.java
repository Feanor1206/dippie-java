package com.dippie.catalog.importing;

import com.dippie.common.error.InvalidRequestException;

// Ngoại lệ con của InvalidRequestException cho lỗi dữ liệu gói danh mục, mang kèm đường dẫn trường JSON bị sai.
public class CatalogInputException extends InvalidRequestException {

    private final String path;

    public CatalogInputException(String path, String message) {
        super(message);
        this.path = path;
    }

    public String path() {
        return path;
    }
}
