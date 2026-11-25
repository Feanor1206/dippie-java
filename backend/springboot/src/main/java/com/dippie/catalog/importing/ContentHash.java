package com.dippie.catalog.importing;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record ContentHash(String value) {

    public static ContentHash of(JsonNode document) {
        return ofText(CatalogJsonWriter.encode(document, true));
    }

    static ContentHash ofText(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return new ContentHash(HexFormat.of().formatHex(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
