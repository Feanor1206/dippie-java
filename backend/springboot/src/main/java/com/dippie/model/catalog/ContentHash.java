package com.dippie.model.catalog;

import com.dippie.common.json.CatalogJsonWriter;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// Giá trị bất biến bọc mã băm SHA-256 của gói hoặc một mục danh mục sau khi chuẩn hóa JSON.
public record ContentHash(String value) {

    // Chuẩn hóa cây JSON theo thứ tự khóa cố định khớp Python rồi tính mã băm SHA-256.
    public static ContentHash of(JsonNode document) {
        return ofText(CatalogJsonWriter.encode(document, true));
    }

    // Băm chuỗi UTF-8 ra mã SHA-256 dạng hex chữ thường.
    public static ContentHash ofText(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return new ContentHash(HexFormat.of().formatHex(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
