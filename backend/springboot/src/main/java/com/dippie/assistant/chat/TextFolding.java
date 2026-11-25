package com.dippie.assistant.chat;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TextFolding {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TextFolding() {
    }

    public static String fold(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String lowered = text.toLowerCase(Locale.ROOT).replace('đ', 'd');
        String normalized = Normalizer.normalize(lowered, Normalizer.Form.NFKD);
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(cp -> Character.getType(cp) != Character.NON_SPACING_MARK)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    public static String collapseWhitespace(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return WHITESPACE.matcher(text.trim()).replaceAll(" ");
    }
}
