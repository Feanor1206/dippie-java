package com.dippie.common.text;

import java.util.Locale;
import java.util.regex.Pattern;

// cắt khoảng trắng và gập hoa thường chuẩn Unicode cho khớp strip với casefold bên Python
public final class UnicodeText {

    public static final String SPACE = "[\\p{IsWhite_Space}\\x1c-\\x1f]";
    private static final Pattern SURROUNDING_SPACE = Pattern.compile("\\A" + SPACE + "+|" + SPACE + "+\\z");

    private UnicodeText() {
    }

    // xóa hết khoảng trắng Unicode ở 2 đầu chuỗi
    public static String strip(String text) {
        return SURROUNDING_SPACE.matcher(text).replaceAll("");
    }

    // gập hoa thường theo luật casefold Unicode để so khớp văn bản không phân biệt hoa thường
    public static String caseFold(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder folded = new StringBuilder(text.length());
        int length = text.length();
        // nhánh ASCII chạy thẳng cho nhanh, còn mấy ký tự đặc biệt như ı, ẞ hay chữ Cherokee xử lý riêng
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
