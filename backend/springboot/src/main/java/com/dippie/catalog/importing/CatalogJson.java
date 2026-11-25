package com.dippie.catalog.importing;

import com.dippie.common.json.JsonText;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.Comparator;

final class CatalogJson {

    static final Comparator<String> CODE_POINT_ORDER = (left, right) ->
            Arrays.compare(left.codePoints().toArray(), right.codePoints().toArray());

    private CatalogJson() {
    }

    static boolean absent(JsonNode node) {
        return node.isNull() || node.isMissingNode();
    }

    static JsonNode or(JsonNode node, JsonNode alternative) {
        return JsonText.isTruthy(node) ? node : alternative;
    }

    static int revision(JsonNode item, int packageRevision) {
        return item.path("record").path("revision").asInt(packageRevision);
    }
}
