package com.dippie.desktop.model;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

// chuẩn hóa chuỗi tiếng Việt để tìm có dấu hay không dấu đều khớp
public final class SearchText {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private SearchText() {
    }

    // tách dấu tổ hợp rồi xóa hết, phải tự đổi đ sang d vì Normalizer không tự tách chữ đ
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.strip().toLowerCase(Locale.ROOT).replace('đ', 'd');
        return MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
    }

    public static boolean contains(String text, String normalizedTerm) {
        return normalize(text).contains(normalizedTerm);
    }
}
