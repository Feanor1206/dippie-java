package com.dippie.common.text;

import java.util.Locale;
import java.util.regex.Pattern;

// Cắt khoảng trắng và gập hoa thường theo chuẩn Unicode, viết cho khớp với hành vi strip và casefold bên Python.
public final class UnicodeText {

    public static final String SPACE = "[\\p{IsWhite_Space}\\x1c-\\x1f]";
    private static final Pattern SURROUNDING_SPACE = Pattern.compile("\\A" + SPACE + "+|" + SPACE + "+\\z");

    private UnicodeText() {
    }

    // Xóa mọi ký tự khoảng trắng Unicode ở hai đầu chuỗi.
    public static String strip(String text) {
        return SURROUNDING_SPACE.matcher(text).replaceAll("");
    }

    // Gập chữ hoa thường theo quy tắc casefold Unicode để so khớp văn bản không phân biệt hoa thường.
    public static String caseFold(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder folded = new StringBuilder(text.length());
        int length = text.length();
        // Nhánh ASCII chạy trực tiếp cho nhanh, còn các ký tự đặc biệt như ı, ẞ hay chữ Cherokee xử lý riêng.
        for (int index = 0; index < length;) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint < 128) {
                folded.append((char) (codePoint >= 'A' && codePoint <= 'Z' ? codePoint + ('a' - 'A') : codePoint));
            } else if (codePoint == 0x0131) {
                folded.append('ı');
            } else if (codePoint == 0x1e9e) {
                folded.append("ss");
            } else {
                String letter = Character.toString(codePoint);
                if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.CHEROKEE) {
                    folded.append(letter.toUpperCase(Locale.ROOT));
                } else {
                    folded.append(letter.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT));
                }
            }
        }
        return folded.toString();
    }
}
