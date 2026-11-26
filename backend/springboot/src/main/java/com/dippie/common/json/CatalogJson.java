package com.dippie.common.json;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Comparator;

// tiện ích đọc node JSON và so chuỗi theo Unicode code point cho khớp bên Python
public final class CatalogJson {

    // String.compareTo của Java so theo UTF-16 code unit nên lệch thứ tự với Python ở ký tự ngoài BMP như emoji
    public static final Comparator<String> CODE_POINT_ORDER = (left, right) -> {
        int leftIndex = 0;
        int rightIndex = 0;
        int leftLength = left.length();
        int rightLength = right.length();
        while (leftIndex < leftLength && rightIndex < rightLength) {
            int leftPoint = left.codePointAt(leftIndex);
            int rightPoint = right.codePointAt(rightIndex);
            if (leftPoint != rightPoint) {
                return Integer.compare(leftPoint, rightPoint);
            }
            leftIndex += Character.charCount(leftPoint);
            rightIndex += Character.charCount(rightPoint);
        }
        return Integer.compare(leftLength - leftIndex, rightLength - rightIndex);
    };

    private CatalogJson() {
    }

    public static boolean absent(JsonNode node) {
        return node.isNull() || node.isMissingNode();
    }

    // lấy node đầu nếu truthy kiểu Python, không thì lấy node thay thế
    public static JsonNode or(JsonNode node, JsonNode alternative) {
        return JsonText.isTruthy(node) ? node : alternative;
    }

    public static int revision(JsonNode item, int packageRevision) {
        return item.path("record").path("revision").asInt(packageRevision);
    }
}
