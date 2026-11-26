package com.dippie.model.catalog;

import com.dippie.common.json.CatalogJsonWriter;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// bọc bất biến mã SHA-256 của gói hay từng mục catalog sau khi chuẩn hóa JSON
public record ContentHash(String value) {

    // sort key JSON cố định cho khớp bên Python rồi băm SHA-256
    public static ContentHash of(JsonNode document) {
        return ofText(CatalogJsonWriter.encode(document, true));
    }

    // băm chuỗi UTF-8 ra hex SHA-256 chữ thường
    public static ContentHash ofText(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return new ContentHash(HexFormat.of().formatHex(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
