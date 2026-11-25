package com.dippie.catalog.importing;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;

final class CatalogJson {

    static final Comparator<String> CODE_POINT_ORDER = (left, right) ->
            Arrays.compare(left.codePoints().toArray(), right.codePoints().toArray());

    private static final Pattern SURROUNDING_SPACE = Pattern.compile("(?U)\\A[\\s\\x1c-\\x1f]+|[\\s\\x1c-\\x1f]+\\z");

    private CatalogJson() {
    }

    static boolean absent(JsonNode node) {
        return node.isNull() || node.isMissingNode();
    }

    static boolean truthy(JsonNode node) {
        if (absent(node)) {
            return false;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual()) {
            return !node.textValue().isEmpty();
        }
        if (node.isNumber()) {
            return node.doubleValue() != 0;
        }
        return !node.isEmpty();
    }

    static JsonNode or(JsonNode node, JsonNode alternative) {
        return truthy(node) ? node : alternative;
    }

    static String strip(String value) {
        return SURROUNDING_SPACE.matcher(value).replaceAll("");
    }

    static String caseFold(String value) {
        StringBuilder folded = new StringBuilder();
        value.codePoints().forEach(point -> {
            String letter = Character.toString(point);
            if (point == 0x0131) {
                folded.append(letter);
            } else if (point == 0x1e9e) {
                folded.append("ss");
            } else if (Character.UnicodeScript.of(point) == Character.UnicodeScript.CHEROKEE) {
                folded.append(letter.toUpperCase(Locale.ROOT));
            } else {
                folded.append(letter.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT));
            }
        });
        return folded.toString();
    }

    static int revision(JsonNode item, int packageRevision) {
        return item.path("record").path("revision").asInt(packageRevision);
    }
}
