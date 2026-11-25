package com.dippie.common.text;

import java.util.Locale;
import java.util.regex.Pattern;

public final class UnicodeText {

    public static final String SPACE = "[\\p{IsWhite_Space}\\x1c-\\x1f]";
    private static final Pattern SURROUNDING_SPACE = Pattern.compile("\\A" + SPACE + "+|" + SPACE + "+\\z");

    private UnicodeText() {
    }

    public static String strip(String value) {
        return SURROUNDING_SPACE.matcher(value).replaceAll("");
    }

    public static String caseFold(String value) {
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
}
