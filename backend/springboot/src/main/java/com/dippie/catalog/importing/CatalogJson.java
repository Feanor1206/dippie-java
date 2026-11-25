package com.dippie.catalog.importing;

import com.dippie.common.json.JsonText;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Comparator;

// Tiện ích đọc nút JSON và so sánh chuỗi theo Unicode code point cho đồng nhất với Python.
final class CatalogJson {

    // String.compareTo của Java so theo UTF-16 code unit nên lệch thứ tự với Python ở các ký tự ngoài BMP như emoji.
    static final Comparator<String> CODE_POINT_ORDER = (left, right) -> {
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

    static boolean absent(JsonNode node) {
        return node.isNull() || node.isMissingNode();
    }

    // Ưu tiên node đầu nếu truthy theo kiểu Python, ngược lại lấy node thay thế.
    static JsonNode or(JsonNode node, JsonNode alternative) {
        return JsonText.isTruthy(node) ? node : alternative;
    }

    static int revision(JsonNode item, int packageRevision) {
        return item.path("record").path("revision").asInt(packageRevision);
    }
}
