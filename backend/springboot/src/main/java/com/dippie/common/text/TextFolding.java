package com.dippie.common.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

// bỏ dấu tiếng Việt, hạ chữ thường và gom khoảng trắng để tìm kiếm không phân biệt dấu
public final class TextFolding {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TextFolding() {
    }

    // hạ chữ thường, tự đổi riêng chữ đ sang d (vì NFKD không tách đ) rồi lọc hết dấu thanh
    public static String fold(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String lowered = text.toLowerCase(Locale.ROOT).replace('đ', 'd');
        String normalized = Normalizer.normalize(lowered, Normalizer.Form.NFKD);
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(codePoint -> Character.getType(codePoint) != Character.NON_SPACING_MARK)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    // cắt khoảng trắng 2 đầu rồi gộp nhiều dấu cách liền nhau thành 1
    public static String collapseWhitespace(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return WHITESPACE.matcher(text.trim()).replaceAll(" ");
    }

    public static String foldNormalized(String text) {
        return collapseWhitespace(fold(text));
    }
}
