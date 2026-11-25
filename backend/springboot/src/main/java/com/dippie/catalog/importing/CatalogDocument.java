package com.dippie.catalog.importing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

public record CatalogDocument(JsonNode json) {

    public CatalogDocument {
        json = json == null ? NullNode.getInstance() : json.deepCopy();
    }

    @Override
    public JsonNode json() {
        return json.deepCopy();
    }
}
